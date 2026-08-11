package org.titiplex.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.titiplex.config.GlottologProperties;

import java.time.Instant;

/**
 * Legacy Java scheduler. Disabled unless {@code app.glottolog.legacy-pipeline-enabled=true}.
 */
@Service
public class GlottologScheduledUpdateService {

    private static final String SCHEDULED_TRIGGER_USERNAME = "system:auto-glottolog";

    private final GlottologAdminService glottologAdminService;
    private final GlottologUpdateJobService glottologUpdateJobService;
    private final GlottologProperties glottologProperties;

    public GlottologScheduledUpdateService(
            GlottologAdminService glottologAdminService,
            GlottologUpdateJobService glottologUpdateJobService,
            GlottologProperties glottologProperties
    ) {
        this.glottologAdminService = glottologAdminService;
        this.glottologUpdateJobService = glottologUpdateJobService;
        this.glottologProperties = glottologProperties;
    }

    @Scheduled(fixedDelayString = "${app.glottolog.scheduler-check-delay-ms:3600000}")
    public void runDueUpdate() {
        if (!glottologProperties.isLegacyPipelineEnabled()) {
            return;
        }
        if (glottologAdminService.isScheduledUpdateDue(Instant.now())) {
            glottologUpdateJobService.startJob(SCHEDULED_TRIGGER_USERNAME);
        }
    }
}
