package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(name = "UpdateScenarioMetadataRequest", description = "Editable metadata for a scenario.")
public record UpdateScenarioMetadataRequest(
        @Schema(description = "Scenario title.", example = "My revised scenario")
        String title,
        @Schema(description = "Scenario description.", example = "Updated description for the scenario.")
        String description,
        @Schema(description = "Tags to associate with the scenario.")
        List<String> tags,
        @Schema(description = "Story location. Omit to retain it; an empty object clears it.")
        ScenarioLocationDto location
) {
}