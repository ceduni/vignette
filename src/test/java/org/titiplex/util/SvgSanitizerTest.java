package org.titiplex.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class SvgSanitizerTest {

    private static final String NS = "xmlns=\"http://www.w3.org/2000/svg\" xmlns:xlink=\"http://www.w3.org/1999/xlink\"";

    private static String clean(String svg) {
        return new String(SvgSanitizer.sanitize(svg.getBytes(StandardCharsets.UTF_8)), StandardCharsets.UTF_8);
    }

    @Test
    void keepsDrawing() {
        String out = clean("<svg " + NS + " viewBox=\"0 0 10 10\"><defs><linearGradient id=\"g\"><stop offset=\"0\"/></linearGradient></defs>"
                + "<rect width=\"10\" height=\"10\" fill=\"url(#g)\" style=\"stroke: red\"/><use href=\"#g\"/><text>Hi</text></svg>");

        assertTrue(out.contains("viewBox=\"0 0 10 10\""));
        assertTrue(out.contains("fill=\"url(#g)\""));
        assertTrue(out.contains("style=\"stroke: red\""));
        assertTrue(out.contains("href=\"#g\""));
        assertTrue(out.contains(">Hi</text>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<script>alert(1)</script>",
            "<foreignObject><body xmlns=\"http://www.w3.org/1999/xhtml\"><img src=\"x\" onerror=\"alert(1)\"/></body></foreignObject>",
            "<a href=\"javascript:alert(1)\"><text>click</text></a>",
            "<set attributeName=\"href\" to=\"javascript:alert(1)\"/>",
            "<animate attributeName=\"href\" values=\"javascript:alert(1)\"/>",
            "<iframe xmlns=\"http://www.w3.org/1999/xhtml\" src=\"javascript:alert(1)\"/>",
    })
    void removesActiveElements(String payload) {
        String out = clean("<svg " + NS + ">" + payload + "<circle r=\"1\"/></svg>");

        assertFalse(out.toLowerCase().contains("alert"), out);
        assertTrue(out.contains("<circle"));
    }

    @Test
    void removesEventHandlersAndScriptLinks() {
        String out = clean("<svg " + NS + " onload=\"alert(1)\"><rect onclick=\"alert(2)\" OnMouseOver=\"alert(3)\"/>"
                + "<use xlink:href=\"javascript:alert(4)\"/><use href=\"https://evil.example/x.svg#a\"/>"
                + "<image href=\"https://evil.example/track.png\"/><rect fill=\"javascript:alert(5)\"/></svg>");

        assertFalse(out.contains("alert"), out);
        assertFalse(out.contains("evil.example"), out);
        assertTrue(out.contains("<rect"));
    }

    @Test
    void keepsEmbeddedRasterImages_only() {
        String png = "data:image/png;base64,iVBORw0KGgo=";
        String out = clean("<svg " + NS + "><image href=\"" + png + "\"/><image href=\"data:image/svg+xml;base64,PHN2Zz4=\"/></svg>");

        assertTrue(out.contains(png));
        assertFalse(out.contains("svg+xml"), out);
    }

    @Test
    void cleansCss() {
        String out = clean("<svg " + NS + "><style>@import url(https://evil.example/a.css); .a{fill:url(#g);background:url(https://evil.example/b.png)}</style>"
                + "<rect style=\"fill: url('https://evil.example/c.png')\"/></svg>");

        assertFalse(out.contains("evil.example"), out);
        assertFalse(out.contains("@import"), out);
        assertTrue(out.contains("url(#g)"));
    }

    @Test
    void removesProcessingInstructions() {
        String out = clean("<?xml-stylesheet href=\"https://evil.example/a.css\"?><svg " + NS + "><rect/></svg>");

        assertFalse(out.contains("evil.example"), out);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // XXE: external entity reading a local file
            "<?xml version=\"1.0\"?><!DOCTYPE svg [<!ENTITY x SYSTEM \"file:///etc/passwd\">]><svg xmlns=\"http://www.w3.org/2000/svg\"><text>&x;</text></svg>",
            // billion laughs
            "<!DOCTYPE svg [<!ENTITY a \"aaaa\"><!ENTITY b \"&a;&a;&a;&a;\">]><svg xmlns=\"http://www.w3.org/2000/svg\"><text>&b;</text></svg>",
            // not an SVG
            "<html><body/></html>",
            "<svg><rect/></svg>",
            "not xml at all",
    })
    void refusesDangerousOrInvalidDocuments(String input) {
        assertThrows(IllegalArgumentException.class, () -> clean(input));
    }

    private static SvgSanitizer.Size size(String svg) {
        return SvgSanitizer.size(svg.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void size_readsWidthAndHeight() {
        assertEquals(new SvgSanitizer.Size(700, 500), size("<svg " + NS + " width=\"700\" height=\"500px\"/>"));
    }

    @Test
    void size_fallsBackToViewBox() {
        assertEquals(new SvgSanitizer.Size(720, 480), size("<svg " + NS + " viewBox=\"0 0 720 480\" width=\"100%\"/>"));
        assertEquals(new SvgSanitizer.Size(10, 20), size("<svg " + NS + " viewBox=\"0,0,10.2,19.6\"/>"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<svg xmlns=\"http://www.w3.org/2000/svg\"/>",
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"50%\" height=\"50%\"/>",
            "<html/>",
            "not xml"
    })
    void size_isNullWhenUnknown(String svg) {
        assertNull(size(svg));
    }

    @Test
    void keepsVignetteMakerExport() {
        String out = clean("<svg " + NS + " viewBox=\"0 0 700 500\" width=\"700\" height=\"500\">"
                + "<image href=\"data:image/png;base64,iVBORw0KGgo=\" x=\"0\" y=\"0\" width=\"700\" height=\"500\" preserveAspectRatio=\"none\"/>"
                + "<g class=\"vm-person\" data-uid=\"1\" transform=\"translate(100,200) scale(1)\" style=\"cursor:grab\"><circle r=\"5\"/></g>"
                + "<g class=\"vm-bubble\"><text font-family=\"sans-serif\" text-anchor=\"middle\">Blablabla</text></g></svg>");

        assertTrue(out.contains("href=\"data:image/png;base64,iVBORw0KGgo=\""));
        assertTrue(out.contains("translate(100,200)"));
        assertTrue(out.contains(">Blablabla</text>"));
    }
}
