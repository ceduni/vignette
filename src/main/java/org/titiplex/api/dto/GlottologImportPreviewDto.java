package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GlottologImportPreview", description = "Preview of a Glottolog import without writing to the database.")
public record GlottologImportPreviewDto(
        @Schema(description = "Total rows in the Glottolog CSV.", example = "27034")
        int sourceRows,

        @Schema(description = "Rows that would be imported after filtering.", example = "9267")
        int selectedRows,

        @Schema(description = "Languages currently stored in the database.", example = "9267")
        long databaseCount
) {
}
