package org.titiplex.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.api.dto.GlottologUpdateJobStatusDto;
import org.titiplex.config.GlottologProperties;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Legacy Java scheduler tests (enabled only with legacy-pipeline-enabled).
 */
@ExtendWith(MockitoExtension.class)
class GlottologScheduledUpdateServiceTest {

    @Mock
    private GlottologAdminService glottologAdminService;

    @Mock
    private GlottologUpdateJobService glottologUpdateJobService;

    @Mock
    private GlottologProperties glottologProperties;

    @InjectMocks
    private GlottologScheduledUpdateService scheduledUpdateService;

    /** Legacy off → no job. */
    @Test
    void runDueUpdate_doesNothing_whenLegacyDisabled() {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(false);

        scheduledUpdateService.runDueUpdate();

        verify(glottologUpdateJobService, never()).startJob(any());
        verify(glottologAdminService, never()).isScheduledUpdateDue(any());
    }

    /** Legacy on + due → startJob system:auto-glottolog. */
    @Test
    void runDueUpdate_startsJob_whenLegacyEnabledAndDue() {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(true);
        when(glottologAdminService.isScheduledUpdateDue(any(Instant.class))).thenReturn(true);
        when(glottologUpdateJobService.startJob("system:auto-glottolog"))
                .thenReturn(mock(GlottologUpdateJobStatusDto.class));

        scheduledUpdateService.runDueUpdate();

        verify(glottologUpdateJobService).startJob(eq("system:auto-glottolog"));
    }

    /** Legacy on + not due → no job. */
    @Test
    void runDueUpdate_doesNothing_whenNotDue() {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(true);
        when(glottologAdminService.isScheduledUpdateDue(any(Instant.class))).thenReturn(false);

        scheduledUpdateService.runDueUpdate();

        verify(glottologUpdateJobService, never()).startJob(any());
    }
}
