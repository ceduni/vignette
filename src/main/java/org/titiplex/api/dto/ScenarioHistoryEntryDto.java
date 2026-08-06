package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ScenarioHistoryEntryDto", description = "A single entry in a scenario's change history.")
public record ScenarioHistoryEntryDto(
        @Schema(description = "ID of the history entry.")
        Long id,
        @Schema(description = "Username of the collaborator who made the change.")
        String actorUsername,
        @Schema(description = "Machine-readable action type.", example = "METADATA_UPDATED")
        String action,
        @Schema(description = "Human-readable summary of the change.", example = "Updated title, description")
        String summary,
        @Schema(description = "When the change happened.")
        Instant createdAt
) {
}
