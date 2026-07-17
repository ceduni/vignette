package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "ScenarioInviteLink", description = "Shareable invite link for a scenario")
public record ScenarioInviteLinkDto(
        Long id,
        Long scenarioId,
        String token,
        @Schema(example = "EDITOR") String role,
        String createdByUsername,
        Instant createdAt,
        Instant expiresAt,
        boolean active,
        Integer maxUses,
        int useCount
) {
}
