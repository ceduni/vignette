package org.titiplex.persistence.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Pipeline runtime state (singleton id = 1), maintained by the Glottolog worker.
 */
@Entity
@Table(name = "glottolog_pipeline_state")
public class GlottologPipelineState {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "next_run_at")
    private Instant nextRunAt;

    @Column(name = "last_check_at")
    private Instant lastCheckAt;

    @Column(name = "last_success_at")
    private Instant lastSuccessAt;

    @Column(name = "current_status", nullable = false, length = 64)
    private String currentStatus = "IDLE";

    @Column(name = "status_message", length = 2000)
    private String statusMessage;

    @Column(name = "last_detected_checksum", length = 128)
    private String lastDetectedChecksum;

    @Column(name = "last_downloaded_checksum", length = 128)
    private String lastDownloadedChecksum;

    @Column(name = "last_validated_checksum", length = 128)
    private String lastValidatedChecksum;

    @Column(name = "last_imported_checksum", length = 128)
    private String lastImportedChecksum;

    @Column(name = "last_error", length = 4000)
    private String lastError;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "next_retry_at")
    private Instant nextRetryAt;

    @Column(name = "lock_owner", length = 255)
    private String lockOwner;

    @Column(name = "lock_expires_at")
    private Instant lockExpiresAt;

    @Column(name = "lock_heartbeat_at")
    private Instant lockHeartbeatAt;

    @Column(name = "active_history_id")
    private Long activeHistoryId;

    @Column(name = "config_observed_at")
    private Instant configObservedAt;

    @Column(name = "config_frequency_days")
    private Integer configFrequencyDays;

    @Column(name = "config_auto_update_enabled")
    private Boolean configAutoUpdateEnabled;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Instant getNextRunAt() {
        return nextRunAt;
    }

    public Instant getLastCheckAt() {
        return lastCheckAt;
    }

    public Instant getLastSuccessAt() {
        return lastSuccessAt;
    }

    public String getCurrentStatus() {
        return currentStatus;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public String getLastDetectedChecksum() {
        return lastDetectedChecksum;
    }

    public String getLastDownloadedChecksum() {
        return lastDownloadedChecksum;
    }

    public String getLastValidatedChecksum() {
        return lastValidatedChecksum;
    }

    public String getLastImportedChecksum() {
        return lastImportedChecksum;
    }

    public String getLastError() {
        return lastError;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public String getLockOwner() {
        return lockOwner;
    }

    public Instant getLockExpiresAt() {
        return lockExpiresAt;
    }

    public Instant getLockHeartbeatAt() {
        return lockHeartbeatAt;
    }

    public Long getActiveHistoryId() {
        return activeHistoryId;
    }

    public Instant getConfigObservedAt() {
        return configObservedAt;
    }

    public Integer getConfigFrequencyDays() {
        return configFrequencyDays;
    }

    public Boolean getConfigAutoUpdateEnabled() {
        return configAutoUpdateEnabled;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
