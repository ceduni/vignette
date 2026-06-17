package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(name = "Scenario", description = "Scenario information")
public record ScenarioDto(
        @Schema(description = "Scenario ID (unique).")
        Long id,
        @Schema(description = "Scenario title.", example = "My scenario")
        String title,
        @Schema(description = "Scenario description.", example = "This is a scenario.")
        String description,
        @Schema(description = "ID of the language used in the scenario.", example = "1234ung")
        String languageId,
        @Schema(description = "Username of the author of the scenario.")
        String authorUsername,
        @Schema(description = "Creation date of the scenario.")
        Instant createdAt,
        @Schema(description = "Publication status.", example = "DRAFT")
        String visibilityStatus,
        @Schema(description = "Publication timestamp.")
        Instant publishedAt,
        @Schema(description = "Storyboard layout mode.", example = "PRESET")
        String storyboardLayoutMode,
        @Schema(description = "Storyboard preset.", example = "GRID_3")
        String storyboardPreset,
        @Schema(description = "Storyboard columns.", example = "3")
        Integer storyboardColumns,
        @Schema(description = "List of tags associated with the scenario.")
        List<String> tags,
        @Schema(description = "ID of the parent scenario if this is a fork, null otherwise.")
        Long parentScenarioId
) {
}