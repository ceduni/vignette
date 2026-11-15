package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The setting of a story, independent of the language's geographic reference point.")
public record ScenarioLocationDto(String name, Double latitude, Double longitude) {
}
