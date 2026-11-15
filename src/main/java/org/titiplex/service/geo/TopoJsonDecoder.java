package org.titiplex.service.geo;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the geometries of a TopoJSON file into plain polygons of [longitude, latitude] points.
 * <p>
 * TopoJSON does not store each border once per country: borders are cut into shared
 * "arcs", and a geometry only lists the indexes of the arcs that make up its rings. Arc
 * points are delta-encoded integers that the file's {@code transform} (scale + translate)
 * converts back to degrees.
 */
public final class TopoJsonDecoder {

    private final JsonNode arcs;
    private final double[] scale;
    private final double[] translate;

    public TopoJsonDecoder(JsonNode topology) {
        this.arcs = topology.get("arcs");
        JsonNode transform = topology.get("transform");
        this.scale = readPair(transform, "scale", 1.0);
        this.translate = readPair(transform, "translate", 0.0);
    }

    /** The polygons of a Polygon or MultiPolygon geometry; empty for any other geometry type. */
    public List<List<List<double[]>>> decodePolygons(JsonNode geometry) {
        JsonNode arcGroups = geometry.get("arcs");
        if (arcGroups == null) {
            return List.of();
        }

        return switch (geometry.path("type").asText("")) {
            case "Polygon" -> List.of(decodeRings(arcGroups));
            case "MultiPolygon" -> decodeMultiPolygon(arcGroups);
            default -> List.of();
        };
    }

    private List<List<List<double[]>>> decodeMultiPolygon(JsonNode multiPolygonArcs) {
        if (!multiPolygonArcs.isArray()) {
            return List.of();
        }

        List<List<List<double[]>>> polygons = new ArrayList<>();
        for (JsonNode polygonArcs : multiPolygonArcs) {
            polygons.add(decodeRings(polygonArcs));
        }
        return polygons;
    }

    /** The rings of one polygon; degenerate rings (fewer than 3 points) are dropped. */
    private List<List<double[]>> decodeRings(JsonNode polygonArcs) {
        if (!polygonArcs.isArray()) {
            return List.of();
        }

        List<List<double[]>> rings = new ArrayList<>();
        for (JsonNode ringArcs : polygonArcs) {
            List<double[]> ring = decodeRing(ringArcs);
            if (ring.size() >= 3) {
                rings.add(ring);
            }
        }
        return rings;
    }

    /** A ring is its arcs laid end to end. */
    private List<double[]> decodeRing(JsonNode ringArcs) {
        if (!ringArcs.isArray()) {
            return List.of();
        }

        List<double[]> points = new ArrayList<>();
        for (JsonNode arcIndexNode : ringArcs) {
            appendArc(points, decodeArc(arcIndexNode.asInt()));
        }
        return points;
    }

    /** A negative index means "arc ~index, walked backwards". */
    private List<double[]> decodeArc(int arcIndex) {
        boolean reversed = arcIndex < 0;
        JsonNode arc = arcs.get(reversed ? ~arcIndex : arcIndex);
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

        return reversed ? points.reversed() : points;
    }

    /** Consecutive arcs share their junction point: it is kept only once. */
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

    private static double[] readPair(JsonNode transform, String field, double fallback) {
        JsonNode node = transform != null ? transform.get(field) : null;
        if (node == null || !node.isArray() || node.size() < 2) {
            return new double[]{fallback, fallback};
        }
        return new double[]{node.get(0).asDouble(fallback), node.get(1).asDouble(fallback)};
    }
}
