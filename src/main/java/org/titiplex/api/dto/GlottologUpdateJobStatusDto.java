package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(
        name = "GlottologUpdateJobStatus",
        description = "Live status of a Glottolog download/import job."
)
public record GlottologUpdateJobStatusDto(
        @Schema(description = "Current job identifier, or null if no job has been started yet.")
        String jobId,

        @Schema(description = "Job state.", example = "RUNNING")
        String state,

        @Schema(description = "Current processing stage.", example = "SYNCING_DATABASE")
        String stage,

        @Schema(description = "Human-readable stage label.", example = "Synchronisation de la base")
        String stageLabel,

        @Schema(description = "Latest detail message shown to the user.")
        String message,

        @Schema(description = "Overall progress percent between 0 and 100.", example = "67")
        int progressPercent,

        @Schema(description = "True when the percent is estimated rather than exact.")
        boolean estimatedProgress,

        @Schema(description = "CSV source rows known for this run.", example = "27034")
        Integer sourceRows,

        @Schema(description = "Rows selected for import after filtering.", example = "13947")
        Integer selectedRows,

        @Schema(description = "Rows processed during the synchronization stage.", example = "8400")
        Integer processedRows,

        @Schema(description = "New languages inserted so far.", example = "120")
        Integer inserted,

        @Schema(description = "Existing languages updated so far.", example = "45")
        Integer updated,

        @Schema(description = "Existing languages left unchanged so far.", example = "8235")
        Integer unchanged,

        @Schema(description = "Languages present in the database after completion, when known.", example = "9387")
        Long databaseCount,

        @Schema(description = "Job start time in UTC.")
        Instant startedAt,

        @Schema(description = "Job end time in UTC, if finished.")
        Instant finishedAt,

        @Schema(description = "Last script/log lines collected for display.")
        List<String> recentLogLines,

        @Schema(description = "Final success result, present only when the job succeeds.")
        GlottologUpdateResultDto result,

        @Schema(description = "Final error message, present only when the job fails.")
        String error
) {
}
