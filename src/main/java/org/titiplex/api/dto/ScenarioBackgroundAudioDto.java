package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ScenarioBackgroundAudio", description = "Scenario-level background audio for ambience or soundtrack.")
public record ScenarioBackgroundAudioDto(
        @Schema(description = "Audio file ID", example = "42")
        Long id,
        @Schema(description = "Display title", example = "Forest ambience")
        String title,
        @Schema(description = "Order inside the scenario background audio list", example = "1")
        Integer idx,
        @Schema(description = "MIME type", example = "audio/mpeg")
        String mime,
        @Schema(description = "Relative API URL used to stream the audio content", example = "/api/audios/42/content")
        String contentUrl,
        @Schema(description = "Optional credit or source label", example = "Freesound user fieldrecordist")
        String sourceLabel,
        @Schema(description = "Optional source URL", example = "https://freesound.org/s/12345/")
        String sourceUrl,
        @Schema(description = "Whether this audio is used by the scenario player")
        boolean active
) {
}
