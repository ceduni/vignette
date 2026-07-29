package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "GlottologUpdateHistoryRow", description = "One historical Glottolog import run.")
public record GlottologUpdateHistoryRowDto(
        @Schema(description = "History row identifier.")
        Long id,

        @Schema(description = "Admin username who triggered the update.")
        String triggeredByUsername,

        @Schema(description = "Run status.", example = "SUCCEEDED")
        String status,

        @Schema(description = "Import start timestamp.")
        Instant startedAt,

        @Schema(description = "Import finish timestamp.")
        Instant finishedAt,

        @Schema(description = "Glottolog version used for this run.")
        String glottologVersion,

        @Schema(description = "Total CSV rows before filtering.")
        Integer sourceRows,

        @Schema(description = "Rows kept after filtering.")
        Integer selectedRows,

        @Schema(description = "Languages inserted.")
        Integer inserted,

        @Schema(description = "Languages updated.")
        Integer updated,

        @Schema(description = "Languages unchanged.")
        Integer unchanged,

        @Schema(description = "Database count after import.")
        Long databaseCount,

        @Schema(description = "Download duration in milliseconds.")
        Long downloadDurationMs,

        @Schema(description = "Synchronization duration in milliseconds.")
        Long syncDurationMs,

        @Schema(description = "Failure message if the run failed.")
        String errorMessage,

        @Schema(description = "How the run was triggered.", example = "AUTO")
        String triggerType,

        @Schema(description = "Final or last pipeline status.", example = "NO_CHANGE")
        String pipelineStatus
) {
}
