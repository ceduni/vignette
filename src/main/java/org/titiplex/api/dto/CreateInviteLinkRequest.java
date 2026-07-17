package org.titiplex.api.dto;

import java.time.Instant;

public record CreateInviteLinkRequest(String role, Instant expiresAt, Integer maxUses) {
}
