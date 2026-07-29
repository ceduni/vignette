package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "UpdateGlottologAdminSettingsRequest", description = "Payload to update Glottolog admin settings (Java-owned config only).")
public record UpdateGlottologAdminSettingsRequest(
        @Schema(description = "Whether automatic scheduling is enabled.")
        Boolean autoUpdateEnabled,

        @Schema(description = "Frequency in days between automatic checks.", example = "30")
        Integer frequencyDays,

        @Schema(description = "Legacy enum frequency (optional compatibility).", example = "ONE_MONTH")
        String updateFrequency,

        @Schema(description = "When true, also enqueue a manual update request after saving settings.")
        Boolean verifyImmediately,

        @Schema(description = "Admin email notified after each update. Empty string clears it.", example = "admin@example.com")
        String notificationEmail
) {
}
