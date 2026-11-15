package org.titiplex.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(name = "GlottologNotificationRow", description = "One Glottolog admin email notification.")
public record GlottologNotificationRowDto(
        Long id,
        Instant createdAt,
        String toEmail,
        String subject,
        String body,
        String deliveryStatus,
        String deliveryDetail,
        Long historyId,
        String triggerType,
        String pipelineStatus
) {
}
