package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GlottologFrequencyOption", description = "Available schedule option for Glottolog updates.")
public record GlottologFrequencyOptionDto(
        @Schema(description = "Internal value.", example = "THREE_MONTHS")
        String value,

        @Schema(description = "Human-readable label.", example = "Tous les 3 mois")
        String label
) {
}
