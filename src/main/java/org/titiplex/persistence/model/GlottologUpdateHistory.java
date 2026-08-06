package org.titiplex.persistence.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "glottolog_update_history")
public class GlottologUpdateHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "triggered_by_username", nullable = false, length = 255)
    private String triggeredByUsername;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GlottologUpdateHistoryStatus status = GlottologUpdateHistoryStatus.RUNNING;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "glottolog_version", length = 64)
    private String glottologVersion;

    @Column(name = "download_duration_ms")
    private Long downloadDurationMs;

    @Column(name = "sync_duration_ms")
    private Long syncDurationMs;

    @Column(name = "source_rows")
    private Integer sourceRows;

    @Column(name = "selected_rows")
    private Integer selectedRows;

    @Column(name = "inserted_count")
    private Integer insertedCount;

    @Column(name = "updated_count")
    private Integer updatedCount;

    @Column(name = "unchanged_count")
    private Integer unchangedCount;

    @Column(name = "database_count")
    private Long databaseCount;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    @Column(name = "trigger_type", length = 32)
    private String triggerType;

    @Column(name = "pipeline_status", length = 64)
    private String pipelineStatus;

    @Column(name = "remote_checksum", length = 128)
    private String remoteChecksum;

    @Column(name = "imported_checksum", length = 128)
    private String importedChecksum;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "worker_id", length = 255)
    private String workerId;

    public Long getId() {
        return id;
    }

    public String getTriggeredByUsername() {
        return triggeredByUsername;
    }

    public void setTriggeredByUsername(String triggeredByUsername) {
        this.triggeredByUsername = triggeredByUsername;
    }

    public GlottologUpdateHistoryStatus getStatus() {
        return status;
    }

    public void setStatus(GlottologUpdateHistoryStatus status) {
        this.status = status;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getGlottologVersion() {
        return glottologVersion;
    }

    public void setGlottologVersion(String glottologVersion) {
        this.glottologVersion = glottologVersion;
    }

    public Long getDownloadDurationMs() {
        return downloadDurationMs;
    }

    public void setDownloadDurationMs(Long downloadDurationMs) {
        this.downloadDurationMs = downloadDurationMs;
    }

    public Long getSyncDurationMs() {
        return syncDurationMs;
    }

    public void setSyncDurationMs(Long syncDurationMs) {
        this.syncDurationMs = syncDurationMs;
    }

    public Integer getSourceRows() {
        return sourceRows;
    }

    public void setSourceRows(Integer sourceRows) {
        this.sourceRows = sourceRows;
    }

    public Integer getSelectedRows() {
        return selectedRows;
    }

    public void setSelectedRows(Integer selectedRows) {
        this.selectedRows = selectedRows;
    }

    public Integer getInsertedCount() {
        return insertedCount;
    }

    public void setInsertedCount(Integer insertedCount) {
        this.insertedCount = insertedCount;
    }

    public Integer getUpdatedCount() {
        return updatedCount;
    }

    public void setUpdatedCount(Integer updatedCount) {
        this.updatedCount = updatedCount;
    }

    public Integer getUnchangedCount() {
        return unchangedCount;
    }

    public void setUnchangedCount(Integer unchangedCount) {
        this.unchangedCount = unchangedCount;
    }

    public Long getDatabaseCount() {
        return databaseCount;
    }

    public void setDatabaseCount(Long databaseCount) {
        this.databaseCount = databaseCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getPipelineStatus() {
        return pipelineStatus;
    }

    public void setPipelineStatus(String pipelineStatus) {
        this.pipelineStatus = pipelineStatus;
    }

    public String getRemoteChecksum() {
        return remoteChecksum;
    }

    public void setRemoteChecksum(String remoteChecksum) {
        this.remoteChecksum = remoteChecksum;
    }

    public String getImportedChecksum() {
        return importedChecksum;
    }

    public void setImportedChecksum(String importedChecksum) {
        this.importedChecksum = importedChecksum;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public String getWorkerId() {
        return workerId;
    }

    public void setWorkerId(String workerId) {
        this.workerId = workerId;
    }
}
