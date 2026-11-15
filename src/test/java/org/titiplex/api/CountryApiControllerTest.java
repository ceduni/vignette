package org.titiplex.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.api.dto.CountryAtCoordinatesDto;
import org.titiplex.service.CountryGeoService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CountryApiControllerTest {

    @Mock
    private CountryGeoService countryGeoService;

    @InjectMocks
    private CountryApiController controller;

    @Test
    void findByCoordinates_delegatesToService() {
        CountryAtCoordinatesDto expected = new CountryAtCoordinatesDto("FRA", "France", 48.8566, 2.3522);
        when(countryGeoService.findCountryByCoordinates(48.8566, 2.3522)).thenReturn(expected);

        CountryAtCoordinatesDto result = controller.findByCoordinates(48.8566, 2.3522);

        assertEquals(expected, result);
    }
}
