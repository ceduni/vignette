package org.titiplex.service.geo;

import java.util.List;

/**
 * The outline of a country: one polygon per separate piece of land (mainland, islands…).
 * A polygon is a list of rings of [longitude, latitude] points: the first ring is the
 * outer border, the following ones are holes (enclaves).
 */
public record CountryShape(String isoA3, String name, List<List<List<double[]>>> polygons) {

    /**
     * Area of the smallest polygon of this country that contains the point, or -1 when the
     * point is outside the country. The area lets the caller prefer the most specific match
     * when shapes overlap (an enclave inside a larger country).
     */
    public double containingPolygonArea(double longitude, double latitude) {
        double smallestArea = Double.MAX_VALUE;
        boolean found = false;

        for (List<List<double[]>> polygon : polygons) {
            if (polygon.isEmpty()) {
                continue;
            }

            List<double[]> exterior = polygon.get(0);
            if (!pointInRing(longitude, latitude, exterior) || isInHole(longitude, latitude, polygon)) {
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

    private static boolean isInHole(double x, double y, List<List<double[]>> polygon) {
        for (int i = 1; i < polygon.size(); i++) {
            if (pointInRing(x, y, polygon.get(i))) {
                return true;
            }
        }
        return false;
    }

    /** Ray casting: a point is inside a ring when a ray from it crosses the border an odd number of times. */
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

    /** Signed area of a ring (shoelace formula), in square degrees. */
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
