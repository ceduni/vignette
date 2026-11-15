package org.titiplex.persistence.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "glottolog_update_request")
public class GlottologUpdateRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "requested_by_username", nullable = false, length = 255)
    private String requestedByUsername;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private GlottologUpdateRequestStatus status = GlottologUpdateRequestStatus.PENDING;

    @Column(name = "claimed_at")
    private Instant claimedAt;

    @Column(name = "claimed_by", length = 255)
    private String claimedBy;

    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "error_message", length = 4000)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public String getRequestedByUsername() {
        return requestedByUsername;
    }

    public void setRequestedByUsername(String requestedByUsername) {
        this.requestedByUsername = requestedByUsername;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public GlottologUpdateRequestStatus getStatus() {
        return status;
    }

    public void setStatus(GlottologUpdateRequestStatus status) {
        this.status = status;
    }

    public Instant getClaimedAt() {
        return claimedAt;
    }

    public String getClaimedBy() {
        return claimedBy;
    }

    public Instant getHeartbeatAt() {
        return heartbeatAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
