package org.titiplex.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.titiplex.api.dto.ApiError;
import org.titiplex.api.dto.CountryAtCoordinatesDto;
import org.titiplex.api.security.PublicOperation;
import org.titiplex.service.CountryGeoService;

@RestController
@RequestMapping("/api/countries")
@Tag(name = "Country", description = "Endpoints for resolving countries from geographic data.")
public class CountryApiController {

    private final CountryGeoService countryGeoService;

    public CountryApiController(CountryGeoService countryGeoService) {
        this.countryGeoService = countryGeoService;
    }

    @Operation(
            summary = "Resolve country from coordinates",
            description = """
                    Returns the unique country whose boundary contains the given latitude and longitude.
                    Uses the same Natural Earth country shapes as the world map.
                    """
    )
    @PublicOperation
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Country resolved successfully",
                    content = @Content(schema = @Schema(implementation = CountryAtCoordinatesDto.class))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid coordinates",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "No country found at coordinates (e.g. ocean)",
                    content = @Content(schema = @Schema(implementation = ApiError.class))
            )
    })
    @GetMapping("/by-coordinates")
    public CountryAtCoordinatesDto findByCoordinates(
            @Parameter(description = "Latitude in decimal degrees (WGS84)", required = true, example = "48.8566")
            @RequestParam double latitude,

            @Parameter(description = "Longitude in decimal degrees (WGS84)", required = true, example = "2.3522")
            @RequestParam double longitude
    ) {
        return countryGeoService.findCountryByCoordinates(latitude, longitude);
    }
}
