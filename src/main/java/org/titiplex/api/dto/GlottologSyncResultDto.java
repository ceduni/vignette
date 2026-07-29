package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "GlottologSyncResult", description = "Result of a Glottolog synchronization run.")
public record GlottologSyncResultDto(
        @Schema(description = "Total rows in the Glottolog CSV.", example = "27034")
        int sourceRows,

        @Schema(description = "Rows selected for import after filtering.", example = "9267")
        int selectedRows,

        @Schema(description = "New languages inserted.", example = "120")
        int inserted,

        @Schema(description = "Existing languages updated from Glottolog.", example = "45")
        int updated,

        @Schema(description = "Existing languages already matching Glottolog.", example = "9102")
        int unchanged,

        @Schema(description = "Languages in the database after sync.", example = "9387")
        long databaseCount,

        @Schema(description = "Sync duration in milliseconds.", example = "3400")
        long durationMs
) {
}
