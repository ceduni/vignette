package org.titiplex.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.titiplex.api.dto.*;
import org.titiplex.config.GlottologProperties;
import org.titiplex.persistence.model.*;
import org.titiplex.persistence.repo.GlottologAdminSettingsRepository;
import org.titiplex.persistence.repo.GlottologNotificationRepository;
import org.titiplex.persistence.repo.GlottologPipelineStateRepository;
import org.titiplex.persistence.repo.GlottologUpdateHistoryRepository;
import org.titiplex.persistence.repo.GlottologUpdateRequestRepository;
import org.titiplex.persistence.repo.LanguageRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class GlottologAdminService {

    private static final long SETTINGS_SINGLETON_ID = 1L;
    private static final long PIPELINE_STATE_SINGLETON_ID = 1L;

    private final GlottologProperties glottologProperties;
    private final LanguageRepository languageRepository;
    private final GlottologAdminSettingsRepository settingsRepository;
    private final GlottologUpdateHistoryRepository historyRepository;
    private final GlottologPipelineStateRepository pipelineStateRepository;
    private final GlottologUpdateRequestRepository updateRequestRepository;
    private final GlottologNotificationRepository notificationRepository;

    public GlottologAdminService(
            GlottologProperties glottologProperties,
            LanguageRepository languageRepository,
            GlottologAdminSettingsRepository settingsRepository,
            GlottologUpdateHistoryRepository historyRepository,
            GlottologPipelineStateRepository pipelineStateRepository,
            GlottologUpdateRequestRepository updateRequestRepository,
            GlottologNotificationRepository notificationRepository
    ) {
        this.glottologProperties = glottologProperties;
        this.languageRepository = languageRepository;
        this.settingsRepository = settingsRepository;
        this.historyRepository = historyRepository;
        this.pipelineStateRepository = pipelineStateRepository;
        this.updateRequestRepository = updateRequestRepository;
        this.notificationRepository = notificationRepository;
    }

    @Transactional
    public GlottologAdminSettingsDto getSettings() {
        return toSettingsDto(loadOrCreateSettings(), loadPipelineState());
    }

    @Transactional
    public GlottologAdminSettingsDto updateSettings(UpdateGlottologAdminSettingsRequest request, String username) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Request body is required.");
        }

        GlottologAdminSettings settings = loadOrCreateSettings();
        if (request.autoUpdateEnabled() != null) {
            settings.setAutoUpdateEnabled(request.autoUpdateEnabled());
        }
        if (request.frequencyDays() != null) {
            settings.setFrequencyDays(validateFrequencyDays(request.frequencyDays()));
            settings.setUpdateFrequency(mapDaysToLegacyFrequency(settings.getFrequencyDays()));
        } else if (request.updateFrequency() != null && !request.updateFrequency().isBlank()) {
            GlottologUpdateFrequency legacy = parseFrequency(request.updateFrequency());
            settings.setUpdateFrequency(legacy);
            if (legacy.isScheduled()) {
                settings.setFrequencyDays((int) legacy.intervalDays());
            } else if (!settings.isAutoUpdateEnabled()) {
                settings.setFrequencyDays(null);
            }
        }
        if (Boolean.TRUE.equals(request.autoUpdateEnabled())
                && (settings.getFrequencyDays() == null || settings.getFrequencyDays() <= 0)) {
            settings.setFrequencyDays(30);
            settings.setUpdateFrequency(GlottologUpdateFrequency.ONE_MONTH);
        }
        if (Boolean.FALSE.equals(request.autoUpdateEnabled())) {
            // Keep frequency_days stored; only disable auto.
            settings.setUpdateFrequency(GlottologUpdateFrequency.MANUAL);
        }
        if (request.notificationEmail() != null) {
            String email = request.notificationEmail().trim();
            settings.setNotificationEmail(email.isEmpty() ? null : email);
        }

        settings.setUpdatedAt(Instant.now());
        settings.setUpdatedByUsername(username);
        settingsRepository.save(settings);

        GlottologUpdateRequestDto enqueued = null;
        if (Boolean.TRUE.equals(request.verifyImmediately())) {
            enqueued = createManualUpdateRequest(username);
        }

        GlottologAdminSettingsDto dto = toSettingsDto(settings, loadPipelineState());
        if (enqueued != null) {
            // DTO unchanged; caller can also hit /update/request. Kept for side-effect only.
        }
        return dto;
    }

    @Transactional
    public GlottologUpdateRequestDto createManualUpdateRequest(String username) {
        Optional<GlottologUpdateRequest> active = updateRequestRepository.findFirstByStatusInOrderByRequestedAtAsc(
                List.of(
                        GlottologUpdateRequestStatus.PENDING,
                        GlottologUpdateRequestStatus.CLAIMED,
                        GlottologUpdateRequestStatus.RUNNING
                )
        );
        if (active.isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A Glottolog update request is already pending or running (id=" + active.get().getId() + ")."
            );
        }

        GlottologUpdateRequest request = new GlottologUpdateRequest();
        request.setRequestedByUsername(username == null || username.isBlank() ? "admin" : username);
        request.setRequestedAt(Instant.now());
        request.setStatus(GlottologUpdateRequestStatus.PENDING);
        return toRequestDto(updateRequestRepository.save(request));
    }

    @Transactional(readOnly = true)
    public List<GlottologUpdateRequestDto> listUpdateRequests() {
        return updateRequestRepository.findTop20ByOrderByRequestedAtDesc().stream()
                .map(this::toRequestDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GlottologUpdateHistoryRowDto> listHistory() {
        return historyRepository.findTop50ByOrderByStartedAtDesc().stream()
                .map(this::toHistoryDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GlottologNotificationRowDto> listNotifications() {
        return notificationRepository.findTop30ByOrderByCreatedAtDesc().stream()
                .map(this::toNotificationDto)
                .toList();
    }

    /** Summary shown on the admin dashboard: languages currently in the database. */
    @Transactional(readOnly = true)
    public GlottologImportPreviewDto preview() {
        return new GlottologImportPreviewDto(languageRepository.count());
    }

    private int validateFrequencyDays(int days) {
        int min = glottologProperties.getFrequencyDaysMin();
        int max = glottologProperties.getFrequencyDaysMax();
        if (days < min || days > max) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "frequencyDays must be between " + min + " and " + max + "."
            );
        }
        return days;
    }

    private GlottologAdminSettings loadOrCreateSettings() {
        return settingsRepository.findById(SETTINGS_SINGLETON_ID)
                .orElseGet(() -> {
                    GlottologAdminSettings settings = new GlottologAdminSettings();
                    settings.setId(SETTINGS_SINGLETON_ID);
                    settings.setAutoUpdateEnabled(false);
                    settings.setUpdateFrequency(GlottologUpdateFrequency.MANUAL);
                    settings.setFrequencyDays(null);
                    return settingsRepository.save(settings);
                });
    }

    private GlottologPipelineState loadPipelineState() {
        return pipelineStateRepository.findById(PIPELINE_STATE_SINGLETON_ID).orElse(null);
    }

    private GlottologUpdateFrequency parseFrequency(String rawValue) {
        try {
            return GlottologUpdateFrequency.valueOf(rawValue.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown Glottolog frequency: " + rawValue);
        }
    }

    private static GlottologUpdateFrequency mapDaysToLegacyFrequency(Integer days) {
        if (days == null) {
            return GlottologUpdateFrequency.MANUAL;
        }
        return switch (days) {
            case 14 -> GlottologUpdateFrequency.TWO_WEEKS;
            case 30 -> GlottologUpdateFrequency.ONE_MONTH;
            case 90 -> GlottologUpdateFrequency.THREE_MONTHS;
            case 180 -> GlottologUpdateFrequency.SIX_MONTHS;
            default -> GlottologUpdateFrequency.MANUAL;
        };
    }

    private GlottologAdminSettingsDto toSettingsDto(
            GlottologAdminSettings settings,
            GlottologPipelineState pipeline
    ) {
        return new GlottologAdminSettingsDto(
                settings.isAutoUpdateEnabled(),
                settings.getFrequencyDays(),
                settings.getUpdateFrequency() == null ? "MANUAL" : settings.getUpdateFrequency().name(),
                glottologProperties.getVersion(),
                settings.getUpdatedAt(),
                settings.getUpdatedByUsername(),
                pipeline == null ? null : pipeline.getNextRunAt(),
                pipeline == null ? null : pipeline.getLastCheckAt(),
                pipeline == null ? null : pipeline.getLastSuccessAt(),
                pipeline == null ? "UNKNOWN" : pipeline.getCurrentStatus(),
                pipeline == null ? null : pipeline.getStatusMessage(),
                pipeline == null ? null : pipeline.getLastImportedChecksum(),
                pipeline == null ? null : pipeline.getLastError(),
                pipeline == null ? null : pipeline.getRetryCount(),
                glottologProperties.getFrequencyDaysMin(),
                glottologProperties.getFrequencyDaysMax(),
                settings.getNotificationEmail()
        );
    }

    private GlottologUpdateRequestDto toRequestDto(GlottologUpdateRequest request) {
        return new GlottologUpdateRequestDto(
                request.getId(),
                request.getRequestedByUsername(),
                request.getRequestedAt(),
                request.getStatus().name(),
                request.getClaimedAt(),
                request.getClaimedBy(),
                request.getHeartbeatAt(),
                request.getStartedAt(),
                request.getFinishedAt(),
                request.getHistoryId(),
                request.getErrorMessage()
        );
    }

    private GlottologUpdateHistoryRowDto toHistoryDto(GlottologUpdateHistory history) {
        return new GlottologUpdateHistoryRowDto(
                history.getId(),
                history.getTriggeredByUsername(),
                history.getStatus().name(),
                history.getStartedAt(),
                history.getFinishedAt(),
                history.getGlottologVersion(),
                history.getSourceRows(),
                history.getSelectedRows(),
                history.getInsertedCount(),
                history.getUpdatedCount(),
                history.getUnchangedCount(),
                history.getDatabaseCount(),
                history.getDownloadDurationMs(),
                history.getSyncDurationMs(),
                history.getErrorMessage(),
                history.getTriggerType(),
                history.getPipelineStatus()
        );
    }

    private GlottologNotificationRowDto toNotificationDto(GlottologNotification notification) {
        return new GlottologNotificationRowDto(
                notification.getId(),
                notification.getCreatedAt(),
                notification.getToEmail(),
                notification.getSubject(),
                notification.getBody(),
                notification.getDeliveryStatus(),
                notification.getDeliveryDetail(),
                notification.getHistoryId(),
                notification.getTriggerType(),
                notification.getPipelineStatus()
        );
    }
}
