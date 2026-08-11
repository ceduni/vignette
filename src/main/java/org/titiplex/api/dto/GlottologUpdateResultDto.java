package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(
        name = "GlottologUpdateResult",
        description = "Result of downloading Glottolog from Zenodo and synchronizing the database."
)
public record GlottologUpdateResultDto(
        @Schema(description = "Glottolog CLDF version downloaded.", example = "5.3")
        String glottologVersion,

        @Schema(description = "Download and conversion duration in milliseconds.", example = "95000")
        long downloadDurationMs,

        @Schema(description = "Last lines of the download script output.")
        String scriptOutput,

        @Schema(description = "Database synchronization result.")
        GlottologSyncResultDto sync,

        @Schema(
                description = "True when the downloaded CSV matches the last successfully imported CSV, so the database sync was skipped.",
                example = "false"
        )
        boolean sourceUnchanged
) {
}
