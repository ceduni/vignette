package org.titiplex.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.titiplex.api.dto.CountryAtCoordinatesDto;
import org.titiplex.service.geo.CountryShape;
import org.titiplex.service.geo.TopoJsonDecoder;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NoSuchElementException;

/**
 * Finds which country a point of the globe is in.
 * <p>
 * Country outlines come from a bundled world map (TopoJSON, identified by ISO numeric
 * codes), matched with a second file that gives the ISO alpha-3 code of each numeric code.
 * Both are loaded once at startup.
 */
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

        JsonNode topology = readResource(TOPOLOGY_RESOURCE);
        JsonNode geometries = topology.path("objects").path("countries").path("geometries");
        if (!geometries.isArray()) {
            throw new IllegalStateException("Invalid topology: missing country geometries");
        }

        TopoJsonDecoder decoder = new TopoJsonDecoder(topology);
        for (JsonNode geometry : geometries) {
            // Countries without a known ISO alpha-3 code are left out
            CountryMeta meta = metaByNumericCode.get(geometry.path("id").asText("").trim());
            if (meta == null) {
                continue;
            }

            List<List<List<double[]>>> polygons = decoder.decodePolygons(geometry);
            if (polygons.isEmpty()) {
                continue;
            }

            String name = firstNonBlank(geometry.path("properties").path("name").asText(null), meta.name());
            countryShapes.add(new CountryShape(meta.isoA3(), name, polygons));
        }

        if (countryShapes.isEmpty()) {
            throw new IllegalStateException("No country shapes loaded from topology");
        }
    }

    /** When several shapes contain the point, the smallest one wins (an enclave over its surrounding country). */
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

        return new CountryAtCoordinatesDto(bestMatch.isoA3(), bestMatch.name(), latitude, longitude);
    }

    private void validateCoordinates(double latitude, double longitude) {
        if (!Double.isFinite(latitude) || latitude < -90 || latitude > 90) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90");
        }
        if (!Double.isFinite(longitude) || longitude < -180 || longitude > 180) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180");
        }
    }

    /** ISO numeric code → alpha-3 code and name; entries without an alpha-3 code are skipped. */
    private Map<String, CountryMeta> loadCountryMeta() throws IOException {
        Map<String, CountryMeta> result = new HashMap<>();
        for (Map.Entry<String, JsonNode> entry : readResource(ISO_MAPPING_RESOURCE).properties()) {
            String isoA3 = entry.getValue().path("isoA3").asText(null);
            if (isoA3 == null || isoA3.isBlank()) {
                continue;
            }
            result.put(entry.getKey(), new CountryMeta(
                    isoA3.toUpperCase(Locale.ROOT),
                    entry.getValue().path("name").asText(null)
            ));
        }
        return result;
    }

    private JsonNode readResource(String path) throws IOException {
        try (InputStream stream = new ClassPathResource(path).getInputStream()) {
            return objectMapper.readTree(stream);
        }
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
}
