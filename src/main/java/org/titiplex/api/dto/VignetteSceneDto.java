package org.titiplex.api.dto;

import java.time.Instant;

public record VignetteSceneDto(Long id, String name, String sceneJson, Instant createdAt, Instant updatedAt) {}
