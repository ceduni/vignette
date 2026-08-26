package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "SelectScenarioBackgroundAudioRequest", description = "Selects the ambience used by the scenario player.")
public record SelectScenarioBackgroundAudioRequest(
        @Schema(description = "Background audio ID", example = "42")
        Long audioId
) {
}
