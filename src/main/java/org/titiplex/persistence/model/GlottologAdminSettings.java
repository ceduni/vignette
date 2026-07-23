package org.titiplex.persistence.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "glottolog_admin_settings")
public class GlottologAdminSettings {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "auto_update_enabled", nullable = false)
    private boolean autoUpdateEnabled = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "update_frequency", nullable = false, length = 32)
    private GlottologUpdateFrequency updateFrequency = GlottologUpdateFrequency.MANUAL;

    /** Preferred schedule interval in days. */
    @Column(name = "frequency_days")
    private Integer frequencyDays;

    /** Admin email notified after each Glottolog update. */
    @Column(name = "notification_email", length = 255)
    private String notificationEmail;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by_username", length = 255)
    private String updatedByUsername;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public boolean isAutoUpdateEnabled() {
        return autoUpdateEnabled;
    }

    public void setAutoUpdateEnabled(boolean autoUpdateEnabled) {
        this.autoUpdateEnabled = autoUpdateEnabled;
    }

    public GlottologUpdateFrequency getUpdateFrequency() {
        return updateFrequency;
    }

    public void setUpdateFrequency(GlottologUpdateFrequency updateFrequency) {
        this.updateFrequency = updateFrequency;
    }

    public Integer getFrequencyDays() {
        return frequencyDays;
    }

    public void setFrequencyDays(Integer frequencyDays) {
        this.frequencyDays = frequencyDays;
    }

    public String getNotificationEmail() {
        return notificationEmail;
    }

    public void setNotificationEmail(String notificationEmail) {
        this.notificationEmail = notificationEmail;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getUpdatedByUsername() {
        return updatedByUsername;
    }

    public void setUpdatedByUsername(String updatedByUsername) {
        this.updatedByUsername = updatedByUsername;
    }
}
