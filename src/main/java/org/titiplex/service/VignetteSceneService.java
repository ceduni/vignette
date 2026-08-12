package org.titiplex.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.api.dto.VignetteSceneDto;
import org.titiplex.persistence.model.VignetteScene;
import org.titiplex.persistence.repo.VignetteSceneRepository;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class VignetteSceneService {

    private final VignetteSceneRepository repo;

    public VignetteSceneService(VignetteSceneRepository repo) {
        this.repo = repo;
    }

    public List<VignetteSceneDto> listByUser(Long userId) {
        return repo.findByUserIdOrderByUpdatedAtDesc(userId)
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public VignetteSceneDto create(Long userId, String name, String sceneJson) {
        String normalizedName = normalizedName(name);
        if (sceneJson == null || sceneJson.isBlank()) {
            throw new IllegalArgumentException("Scene data is required");
        }
        VignetteScene scene = new VignetteScene();
        scene.setUserId(userId);
        scene.setName(normalizedName);
        scene.setSceneJson(sceneJson);
        return toDto(repo.save(scene));
    }

    @Transactional
    public VignetteSceneDto update(Long sceneId, Long userId, String name, String sceneJson) {
        VignetteScene scene = repo.findByIdAndUserId(sceneId, userId)
                .orElseThrow(() -> new NoSuchElementException("Scene not found"));
        if (name != null) scene.setName(normalizedName(name));
        if (sceneJson != null) {
            if (sceneJson.isBlank()) {
                throw new IllegalArgumentException("Scene data is required");
            }
            scene.setSceneJson(sceneJson);
        }
        scene.setUpdatedAt(Instant.now());
        return toDto(repo.save(scene));
    }

    @Transactional
    public void delete(Long sceneId, Long userId) {
        VignetteScene scene = repo.findByIdAndUserId(sceneId, userId)
                .orElseThrow(() -> new NoSuchElementException("Scene not found"));
        repo.delete(scene);
    }

    private String normalizedName(String name) {
        String normalized = name == null || name.isBlank() ? "Untitled Scene" : name.trim();
        if (normalized.length() > 200) {
            throw new IllegalArgumentException("Scene name must be 200 characters or fewer");
        }
        return normalized;
    }

    private VignetteSceneDto toDto(VignetteScene s) {
        return new VignetteSceneDto(s.getId(), s.getName(), s.getSceneJson(), s.getCreatedAt(), s.getUpdatedAt());
    }
}
