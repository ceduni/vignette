package org.titiplex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.CountryAtCoordinatesDto;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

@Service
public class CountryGeoService {

    private static final String TOPOLOGY_RESOURCE = "geo/countries-110m.json";
    private static final String ISO_MAPPING_RESOURCE = "geo/country-numeric-iso3.json";

    private final ObjectMapper objectMapper;
    private final List<CountryShape> countryShapes = new ArrayList<>();

    public CountryGeoService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @PostConstruct
    void loadCountryShapes() throws IOException {
        Map<String, CountryMeta> metaByNumericCode = loadCountryMeta();

        try (InputStream topologyStream = new ClassPathResource(TOPOLOGY_RESOURCE).getInputStream()) {
            JsonNode topology = objectMapper.readTree(topologyStream);
            JsonNode arcs = topology.get("arcs");
            JsonNode transform = topology.get("transform");
            double[] scale = readScale(transform);
            double[] translate = readTranslate(transform);

            JsonNode geometries = topology.path("objects").path("countries").path("geometries");
            if (!geometries.isArray()) {
                throw new IllegalStateException("Invalid topology: missing country geometries");
            }

            for (JsonNode geometry : geometries) {
                String numericCode = String.valueOf(geometry.path("id").asText("")).trim();
                if (numericCode.isBlank()) {
                    continue;
                }

                CountryMeta meta = metaByNumericCode.get(numericCode);
                String isoA3 = meta != null ? meta.isoA3() : null;
                if (isoA3 == null || isoA3.isBlank()) {
                    continue;
                }

                String name = firstNonBlank(
                        geometry.path("properties").path("name").asText(null),
                        meta.name()
                );

                List<List<List<double[]>>> polygons = decodeGeometry(geometry, arcs, scale, translate);
                if (polygons.isEmpty()) {
                    continue;
                }

                countryShapes.add(new CountryShape(isoA3, name, polygons));
            }
        }

        if (countryShapes.isEmpty()) {
            throw new IllegalStateException("No country shapes loaded from topology");
        }
    }

    public CountryAtCoordinatesDto findCountryByCoordinates(double latitude, double longitude) {
        validateCoordinates(latitude, longitude);

        CountryShape bestMatch = null;
        double smallestArea = Double.MAX_VALUE;

        for (CountryShape shape : countryShapes) {
            double area = shape.containingPolygonArea(longitude, latitude);
            if (area >= 0 && area < smallestArea) {
                smallestArea = area;
                bestMatch = shape;
            }
        }

        if (bestMatch == null) {
            throw new NoSuchElementException("No country found at coordinates");
        }

        return new CountryAtCoordinatesDto(
                bestMatch.isoA3(),
                bestMatch.name(),
                latitude,
                longitude
        );
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    private Map<String, CountryMeta> loadCountryMeta() throws IOException {
        try (InputStream mappingStream = new ClassPathResource(ISO_MAPPING_RESOURCE).getInputStream()) {
            JsonNode mapping = objectMapper.readTree(mappingStream);
            Map<String, CountryMeta> result = new HashMap<>();

            Iterator<Map.Entry<String, JsonNode>> fields = mapping.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String numericCode = entry.getKey();
                JsonNode value = entry.getValue();
                String isoA3 = value.path("isoA3").asText(null);
                if (isoA3 == null || isoA3.isBlank()) {
                    continue;
                }
                result.put(numericCode, new CountryMeta(
                        isoA3.toUpperCase(Locale.ROOT),
                        value.path("name").asText(null)
                ));
            }

            return result;
        }
    }

    private static double[] readScale(JsonNode transform) {
        JsonNode scaleNode = transform != null ? transform.get("scale") : null;
        if (scaleNode == null || !scaleNode.isArray() || scaleNode.size() < 2) {
            return new double[]{1.0, 1.0};
        }
        return new double[]{scaleNode.get(0).asDouble(1.0), scaleNode.get(1).asDouble(1.0)};
    }

    private static double[] readTranslate(JsonNode transform) {
        JsonNode translateNode = transform != null ? transform.get("translate") : null;
        if (translateNode == null || !translateNode.isArray() || translateNode.size() < 2) {
            return new double[]{0.0, 0.0};
        }
        return new double[]{translateNode.get(0).asDouble(0.0), translateNode.get(1).asDouble(0.0)};
    }

    private static List<List<List<double[]>>> decodeGeometry(
            JsonNode geometry,
            JsonNode arcs,
            double[] scale,
            double[] translate
    ) {
        String type = geometry.path("type").asText("");
        JsonNode arcGroups = geometry.get("arcs");
        if (arcGroups == null) {
            return List.of();
        }

        return switch (type) {
            case "Polygon" -> List.of(decodePolygonRings(arcGroups, arcs, scale, translate));
            case "MultiPolygon" -> decodeMultiPolygon(arcGroups, arcs, scale, translate);
            default -> List.of();
        };
    }

    private static List<List<List<double[]>>> decodeMultiPolygon(
            JsonNode multiPolygonArcs,
            JsonNode arcs,
            double[] scale,
            double[] translate
    ) {
        if (!multiPolygonArcs.isArray()) {
            return List.of();
        }

        List<List<List<double[]>>> polygons = new ArrayList<>();
        for (JsonNode polygonArcs : multiPolygonArcs) {
            polygons.add(decodePolygonRings(polygonArcs, arcs, scale, translate));
        }
        return polygons;
    }

    private static List<List<double[]>> decodePolygonRings(
            JsonNode polygonArcs,
            JsonNode arcs,
            double[] scale,
            double[] translate
    ) {
        if (!polygonArcs.isArray()) {
            return List.of();
        }

        List<List<double[]>> rings = new ArrayList<>();
        for (JsonNode ringArcs : polygonArcs) {
            List<double[]> ring = decodeRing(ringArcs, arcs, scale, translate);
            if (ring.size() >= 3) {
                rings.add(ring);
            }
        }
        return rings;
    }

    private static List<double[]> decodeRing(
            JsonNode ringArcs,
            JsonNode arcs,
            double[] scale,
            double[] translate
    ) {
        if (!ringArcs.isArray()) {
            return List.of();
        }

        List<double[]> points = new ArrayList<>();
        for (JsonNode arcIndexNode : ringArcs) {
            int arcIndex = arcIndexNode.asInt();
            List<double[]> arcPoints = decodeArc(arcIndex, arcs, scale, translate);
            appendArc(points, arcPoints);
        }
        return points;
    }

    private static List<double[]> decodeArc(
            int arcIndex,
            JsonNode arcs,
            double[] scale,
            double[] translate
    ) {
        boolean reversed = arcIndex < 0;
        int resolvedIndex = reversed ? ~arcIndex : arcIndex;
        JsonNode arc = arcs.get(resolvedIndex);
        if (arc == null || !arc.isArray()) {
            return List.of();
        }

        double x = 0;
        double y = 0;
        List<double[]> points = new ArrayList<>();

        for (JsonNode delta : arc) {
            if (!delta.isArray() || delta.size() < 2) {
                continue;
            }
            x += delta.get(0).asDouble();
            y += delta.get(1).asDouble();
            points.add(new double[]{
                    x * scale[0] + translate[0],
                    y * scale[1] + translate[1]
            });
        }

        if (reversed) {
            List<double[]> reversedPoints = new ArrayList<>(points.size());
            for (int i = points.size() - 1; i >= 0; i--) {
                reversedPoints.add(points.get(i));
            }
            return reversedPoints;
        }

        return points;
    }

    private static void appendArc(List<double[]> target, List<double[]> arcPoints) {
        if (arcPoints.isEmpty()) {
            return;
        }

        int startIndex = 0;
        if (!target.isEmpty()) {
            double[] last = target.get(target.size() - 1);
            double[] first = arcPoints.get(0);
            if (almostEqual(last[0], first[0]) && almostEqual(last[1], first[1])) {
                startIndex = 1;
            }
        }

        for (int i = startIndex; i < arcPoints.size(); i++) {
            target.add(arcPoints.get(i));
        }
    }

    private static boolean almostEqual(double a, double b) {
        return Math.abs(a - b) < 1e-9;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private record CountryMeta(String isoA3, String name) {
    }

    private static final class CountryShape {
        private final String isoA3;
        private final String name;
        private final List<List<List<double[]>>> polygons;

        private CountryShape(String isoA3, String name, List<List<List<double[]>>> polygons) {
            this.isoA3 = isoA3;
            this.name = name;
            this.polygons = polygons;
        }

        private String isoA3() {
            return isoA3;
        }

        private String name() {
            return name;
        }

        private double containingPolygonArea(double longitude, double latitude) {
            double smallestArea = Double.MAX_VALUE;
            boolean found = false;

            for (List<List<double[]>> polygon : polygons) {
                if (polygon.isEmpty()) {
                    continue;
                }

                List<double[]> exterior = polygon.get(0);
                if (!pointInRing(longitude, latitude, exterior)) {
                    continue;
                }

                boolean inHole = false;
                for (int i = 1; i < polygon.size(); i++) {
                    if (pointInRing(longitude, latitude, polygon.get(i))) {
                        inHole = true;
                        break;
                    }
                }
                if (inHole) {
                    continue;
                }

                double area = Math.abs(ringArea(exterior));
                if (area < smallestArea) {
                    smallestArea = area;
                    found = true;
                }
            }

            return found ? smallestArea : -1;
        }

        private static boolean pointInRing(double x, double y, List<double[]> ring) {
            boolean inside = false;
            int size = ring.size();
            if (size < 3) {
                return false;
            }

            for (int i = 0, j = size - 1; i < size; j = i++) {
                double xi = ring.get(i)[0];
                double yi = ring.get(i)[1];
                double xj = ring.get(j)[0];
                double yj = ring.get(j)[1];

                boolean intersects = ((yi > y) != (yj > y))
                        && (x < (xj - xi) * (y - yi) / (yj - yi) + xi);
                if (intersects) {
                    inside = !inside;
                }
            }

            return inside;
        }

        private static double ringArea(List<double[]> ring) {
            double area = 0;
            int size = ring.size();
            for (int i = 0, j = size - 1; i < size; j = i++) {
                double xi = ring.get(i)[0];
                double yi = ring.get(i)[1];
                double xj = ring.get(j)[0];
                double yj = ring.get(j)[1];
                area += (xj + xi) * (yj - yi);
            }
            return area / 2.0;
        }
    }
}
