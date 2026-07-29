package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "GlottologAdminSettings", description = "Glottolog admin configuration and pipeline status.")
public record GlottologAdminSettingsDto(
        @Schema(description = "Whether automatic updates are enabled.")
        boolean autoUpdateEnabled,

        @Schema(description = "Frequency in days between automatic checks.", example = "30")
        Integer frequencyDays,

        @Schema(description = "Legacy enum frequency kept for compatibility.", example = "ONE_MONTH")
        String updateFrequency,

        @Schema(description = "Glottolog version configured on the server.", example = "5.3")
        String glottologVersion,

        @Schema(description = "Last settings update timestamp.")
        Instant updatedAt,

        @Schema(description = "Admin username who last updated the settings.")
        String updatedByUsername,

        @Schema(description = "Next automatic run. Null when inactive or unknown.")
        Instant nextScheduledUpdateAt,

        @Schema(description = "Last Zenodo check time.")
        Instant lastCheckAt,

        @Schema(description = "Last successful import time.")
        Instant lastSuccessAt,

        @Schema(description = "Current pipeline status.", example = "IDLE")
        String pipelineStatus,

        @Schema(description = "Pipeline status message.")
        String pipelineStatusMessage,

        @Schema(description = "Last checksum imported successfully.")
        String lastImportedChecksum,

        @Schema(description = "Last pipeline error.")
        String lastError,

        @Schema(description = "Current retry count.")
        Integer retryCount,

        @Schema(description = "True when the legacy Java pipeline is still enabled.")
        boolean legacyPipelineEnabled,

        @Schema(description = "Minimum allowed frequency days.")
        int frequencyDaysMin,

        @Schema(description = "Maximum allowed frequency days.")
        int frequencyDaysMax,

        @Schema(description = "Email notified after each Glottolog update.")
        String notificationEmail
) {
}
