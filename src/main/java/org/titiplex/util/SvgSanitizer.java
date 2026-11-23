package org.titiplex.util;

import org.w3c.dom.*;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns an uploaded SVG into a drawing-only SVG, so it cannot run code when it is opened.
 *
 * 
 *
 * Throws {@link IllegalArgumentException} when the file is not a well-formed SVG document.
 */
public final class SvgSanitizer {

    private static final String SVG_NS = "http://www.w3.org/2000/svg";
    private static final String XLINK_NS = "http://www.w3.org/1999/xlink";

    private static final Set<String> ALLOWED_ELEMENTS = Set.of(
            "svg", "g", "defs", "symbol", "use", "title", "desc", "metadata", "switch", "style",
            "path", "rect", "circle", "ellipse", "line", "polyline", "polygon",
            "text", "tspan", "textPath",
            "image",
            "linearGradient", "radialGradient", "stop", "pattern",
            "clipPath", "mask", "marker",
            "filter", "feBlend", "feColorMatrix", "feComponentTransfer", "feComposite",
            "feConvolveMatrix", "feDiffuseLighting", "feDisplacementMap", "feDistantLight",
            "feDropShadow", "feFlood", "feFuncA", "feFuncB", "feFuncG", "feFuncR",
            "feGaussianBlur", "feMerge", "feMergeNode", "feMorphology", "feOffset",
            "fePointLight", "feSpecularLighting", "feSpotLight", "feTile", "feTurbulence"
    );

    private static final Pattern SAFE_DATA_IMAGE =
            Pattern.compile("^data:image/(png|jpe?g|gif|webp);base64,[a-z0-9+/=\\s]*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_IMPORT = Pattern.compile("@import[^;]*;?", Pattern.CASE_INSENSITIVE);
    private static final Pattern CSS_URL = Pattern.compile("url\\s*\\(\\s*(['\"]?)(.*?)\\1\\s*\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern DANGEROUS_VALUE =
            Pattern.compile("javascript:|vbscript:|data:text/html|expression\\s*\\(", Pattern.CASE_INSENSITIVE);

    private SvgSanitizer() {
    }

    public static byte[] sanitize(byte[] svg) {
        Document document = parse(svg);
        Element root = document.getDocumentElement();
        if (!"svg".equals(root.getLocalName()) || !SVG_NS.equals(root.getNamespaceURI())) {
            throw new IllegalArgumentException("not an SVG image");
        }
        cleanChildren(document); // cleans the root element and everything below it
        return serialize(document);
    }

    // --- parsing / writing ---------------------------------------------------

    private static Document parse(byte[] svg) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            return builder.parse(new InputSource(new ByteArrayInputStream(svg)));
        } catch (SAXException | IOException e) {
            throw new IllegalArgumentException("invalid SVG image");
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("XML parser cannot be configured safely", e);
        }
    }

    private static byte[] serialize(Document document) {
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
            Transformer transformer = factory.newTransformer();
            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            transformer.transform(new DOMSource(document), new StreamResult(out));
            return out.toByteArray();
        } catch (TransformerException e) {
            throw new IllegalStateException("cannot write sanitized SVG", e);
        }
    }

    // --- cleaning ------------------------------------------------------------

    private static void cleanChildren(Node parent) {
        for (Node child : List.copyOf(asList(parent.getChildNodes()))) {
            switch (child.getNodeType()) {
                case Node.ELEMENT_NODE -> {
                    Element element = (Element) child;
                    if (isAllowed(element)) {
                        cleanElement(element);
                    } else {
                        parent.removeChild(element);
                    }
                }
                case Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    if ("style".equals(parent.getLocalName())) {
                        child.setNodeValue(cleanCss(child.getNodeValue()));
                    }
                }
                case Node.COMMENT_NODE -> {
                }
                // processing instructions (<?xml-stylesheet?> loads external CSS), entity refs, ...
                default -> parent.removeChild(child);
            }
        }
    }

    private static void cleanElement(Element element) {
        NamedNodeMap attributes = element.getAttributes();
        List<Attr> toRemove = new ArrayList<>();
        for (int i = 0; i < attributes.getLength(); i++) {
            Attr attr = (Attr) attributes.item(i);
            String cleaned = cleanAttribute(element, attr);
            if (cleaned == null) {
                toRemove.add(attr);
            } else if (!cleaned.equals(attr.getValue())) {
                attr.setValue(cleaned);
            }
        }
        toRemove.forEach(element::removeAttributeNode);
        cleanChildren(element);
    }

    private static boolean isAllowed(Element element) {
        return SVG_NS.equals(element.getNamespaceURI()) && ALLOWED_ELEMENTS.contains(element.getLocalName());
    }

    /** The value to keep for this attribute, or null to remove it. */
    private static String cleanAttribute(Element element, Attr attr) {
        String ns = attr.getNamespaceURI();
        String name = attr.getLocalName() != null ? attr.getLocalName() : attr.getName();
        String value = attr.getValue();

        if (XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(ns)) {
            return value;
        }
        if (ns != null && !XLINK_NS.equals(ns)) {
            return null; // xml:base, foreign namespaces, ...
        }
        if (name.toLowerCase(Locale.ROOT).startsWith("on")) {
            return null;
        }
        if ("href".equals(name)) {
            return isSafeHref(element, value) ? value : null;
        }
        if (DANGEROUS_VALUE.matcher(value).find()) {
            return null;
        }
        return value.toLowerCase(Locale.ROOT).contains("url(") ? cleanCss(value) : value;
    }

    /** Links may only point inside the document, or embed a raster image in an image element. */
    private static boolean isSafeHref(Element element, String value) {
        String trimmed = value.trim();
        if (trimmed.startsWith("#")) {
            return true;
        }
        return "image".equals(element.getLocalName()) && SAFE_DATA_IMAGE.matcher(trimmed).matches();
    }

    private static String cleanCss(String css) {
        if (css == null) return null;
        String withoutImports = CSS_IMPORT.matcher(css).replaceAll("");
        Matcher matcher = CSS_URL.matcher(withoutImports);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String target = matcher.group(2).trim();
            boolean safe = target.startsWith("#") || SAFE_DATA_IMAGE.matcher(target).matches();
            matcher.appendReplacement(out, Matcher.quoteReplacement(safe ? matcher.group() : "none"));
        }
        matcher.appendTail(out);
        String cleaned = out.toString();
        return DANGEROUS_VALUE.matcher(cleaned).find() ? "" : cleaned;
    }

    private static List<Node> asList(NodeList nodes) {
        List<Node> list = new ArrayList<>(nodes.getLength());
        for (int i = 0; i < nodes.getLength(); i++) list.add(nodes.item(i));
        return list;
    }
}
