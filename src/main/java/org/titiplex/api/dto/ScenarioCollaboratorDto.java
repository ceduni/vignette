package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ScenarioCollaborator", description = "Collaborator on a scenario")
public record ScenarioCollaboratorDto(
        Long id,
        Long scenarioId,
        String scenarioTitle,
        Long userId,
        String username,
        String displayName,
        @Schema(example = "EDITOR") String role,
        @Schema(example = "ACCEPTED") String status,
        String invitedByUsername,
        Instant invitedAt,
        Instant respondedAt,
        Instant expiresAt
) {
}
