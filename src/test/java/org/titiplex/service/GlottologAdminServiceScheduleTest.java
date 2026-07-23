package org.titiplex.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.titiplex.config.GlottologProperties;
import org.titiplex.persistence.model.GlottologAdminSettings;
import org.titiplex.persistence.model.GlottologUpdateFrequency;
import org.titiplex.persistence.model.GlottologUpdateHistory;
import org.titiplex.persistence.model.GlottologUpdateHistoryStatus;
import org.titiplex.persistence.repo.GlottologAdminSettingsRepository;
import org.titiplex.persistence.repo.GlottologPipelineStateRepository;
import org.titiplex.persistence.repo.GlottologUpdateHistoryRepository;
import org.titiplex.persistence.repo.GlottologUpdateRequestRepository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Legacy Java due-check (only when legacy pipeline is enabled).
 */
@ExtendWith(MockitoExtension.class)
class GlottologAdminServiceScheduleTest {

    private static final long SETTINGS_ID = 1L;

    @Mock
    private GlottologProperties glottologProperties;

    @Mock
    private GlottologUpdateService glottologUpdateService;

    @Mock
    private GlottologAdminSettingsRepository settingsRepository;

    @Mock
    private GlottologUpdateHistoryRepository historyRepository;

    @Mock
    private GlottologPipelineStateRepository pipelineStateRepository;

    @Mock
    private GlottologUpdateRequestRepository updateRequestRepository;

    @InjectMocks
    private GlottologAdminService glottologAdminService;

    @BeforeEach
    void enableLegacyForDueChecks() {
        lenient().when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(true);
    }

    /** Legacy off → never due (Python owns scheduling). */
    @Test
    void isScheduledUpdateDue_false_whenLegacyDisabled() {
        when(glottologProperties.isLegacyPipelineEnabled()).thenReturn(false);
        assertFalse(glottologAdminService.isScheduledUpdateDue(Instant.parse("2026-07-16T15:00:00Z")));
    }

    /** Auto-update désactivé → jamais dû. */
    @Test
    void isScheduledUpdateDue_false_whenAutoUpdateDisabled() {
        stubSettings(false, 14);

        Instant now = Instant.parse("2026-07-16T15:00:00Z");
        assertFalse(glottologAdminService.isScheduledUpdateDue(now));
    }

    /** Fréquence absente → jamais dû. */
    @Test
    void isScheduledUpdateDue_false_whenFrequencyDaysMissing() {
        stubSettings(true, null);

        Instant now = Instant.parse("2026-07-16T15:00:00Z");
        assertFalse(glottologAdminService.isScheduledUpdateDue(now));
    }

    /** Aucun succès → dû immédiatement. */
    @Test
    void isScheduledUpdateDue_true_whenNoPriorSuccess() {
        stubSettings(true, 14);
        when(historyRepository.findTop1ByStatusOrderByFinishedAtDesc(GlottologUpdateHistoryStatus.SUCCEEDED))
                .thenReturn(Optional.empty());

        Instant now = Instant.parse("2026-07-16T15:00:00Z");
        assertTrue(glottologAdminService.isScheduledUpdateDue(now));
    }

    /** À J+13 pour 14 jours → pas dû. */
    @Test
    void isScheduledUpdateDue_false_beforeIntervalElapsed() {
        Instant lastSuccessAt = Instant.parse("2026-07-01T15:42:00Z");
        stubSettings(true, 14);
        stubLastSuccess(lastSuccessAt);

        Instant day13 = lastSuccessAt.plus(13, ChronoUnit.DAYS);
        assertFalse(glottologAdminService.isScheduledUpdateDue(day13));
    }

    /** Pile à J+14 → dû. */
    @Test
    void isScheduledUpdateDue_true_whenIntervalElapsed() {
        Instant lastSuccessAt = Instant.parse("2026-07-01T15:42:00Z");
        stubSettings(true, 14);
        stubLastSuccess(lastSuccessAt);

        Instant day14 = lastSuccessAt.plus(14, ChronoUnit.DAYS);
        assertTrue(glottologAdminService.isScheduledUpdateDue(day14));
    }

    private void stubSettings(boolean autoEnabled, Integer frequencyDays) {
        GlottologAdminSettings settings = new GlottologAdminSettings();
        settings.setId(SETTINGS_ID);
        settings.setAutoUpdateEnabled(autoEnabled);
        settings.setFrequencyDays(frequencyDays);
        settings.setUpdateFrequency(
                frequencyDays != null && frequencyDays == 14
                        ? GlottologUpdateFrequency.TWO_WEEKS
                        : GlottologUpdateFrequency.MANUAL
        );
        when(settingsRepository.findById(SETTINGS_ID)).thenReturn(Optional.of(settings));
    }

    private void stubLastSuccess(Instant finishedAt) {
        GlottologUpdateHistory history = new GlottologUpdateHistory();
        history.setStatus(GlottologUpdateHistoryStatus.SUCCEEDED);
        history.setFinishedAt(finishedAt);
        history.setStartedAt(finishedAt.minus(5, ChronoUnit.MINUTES));
        history.setTriggeredByUsername("admin");
        when(historyRepository.findTop1ByStatusOrderByFinishedAtDesc(GlottologUpdateHistoryStatus.SUCCEEDED))
                .thenReturn(Optional.of(history));
    }
}
