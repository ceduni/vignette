package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CountryAtCoordinatesDto", description = "Country resolved from geographic coordinates.")
public record CountryAtCoordinatesDto(
        @Schema(description = "ISO 3166-1 alpha-3 country code", example = "FRA")
        String isoA3,
        @Schema(description = "Common country name", example = "France")
        String name,
        @Schema(description = "Latitude used for lookup", example = "48.8566")
        double latitude,
        @Schema(description = "Longitude used for lookup", example = "2.3522")
        double longitude
) {
}
