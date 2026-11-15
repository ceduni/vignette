package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "GlottologUpdateRequest", description = "Manual Glottolog update request deposited by an admin.")
public record GlottologUpdateRequestDto(
        Long id,
        String requestedByUsername,
        Instant requestedAt,
        String status,
        Instant claimedAt,
        String claimedBy,
        Instant heartbeatAt,
        Instant startedAt,
        Instant finishedAt,
        Long historyId,
        String errorMessage
) {
}
