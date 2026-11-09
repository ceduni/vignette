package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GlottologImportPreview", description = "Summary of the Glottolog catalogue currently in the database.")
public record GlottologImportPreviewDto(
        @Schema(description = "Languages currently stored in the database.", example = "9267")
        long databaseCount
) {
}
