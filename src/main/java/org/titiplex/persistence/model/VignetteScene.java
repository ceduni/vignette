package org.titiplex.persistence.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "vignette_maker_scene")
public class VignetteScene {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 200)
    private String name = "Untitled Scene";

    @Column(name = "scene_json", nullable = false, columnDefinition = "TEXT")
    private String sceneJson;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public User getUser() { return user; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getSceneJson() { return sceneJson; }
    public void setSceneJson(String sceneJson) { this.sceneJson = sceneJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
