package org.titiplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.titiplex.api.dto.CountryAtCoordinatesDto;

import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CountryGeoServiceTest {

    private CountryGeoService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new CountryGeoService(new com.fasterxml.jackson.databind.ObjectMapper());
        service.loadCountryShapes();
    }

    @Test
    void findCountryByCoordinates_returnsFranceForParis() {
        CountryAtCoordinatesDto result = service.findCountryByCoordinates(48.8566, 2.3522);

        assertEquals("FRA", result.isoA3());
        assertEquals(48.8566, result.latitude());
        assertEquals(2.3522, result.longitude());
    }

    @Test
    void findCountryByCoordinates_returnsCanadaForMontreal() {
        CountryAtCoordinatesDto result = service.findCountryByCoordinates(45.5017, -73.5673);

        assertEquals("CAN", result.isoA3());
    }

    @Test
    void findCountryByCoordinates_rejectsInvalidLatitude() {
        assertThrows(IllegalArgumentException.class, () -> service.findCountryByCoordinates(120, 2.0));
    }

    @Test
    void findCountryByCoordinates_rejectsInvalidLongitude() {
        assertThrows(IllegalArgumentException.class, () -> service.findCountryByCoordinates(10, 200));
    }

    @Test
    void findCountryByCoordinates_returnsNotFoundForOceanPoint() {
        assertThrows(NoSuchElementException.class, () -> service.findCountryByCoordinates(0, -160));
    }
}
