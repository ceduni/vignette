package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.VignetteScene;

import java.util.List;
import java.util.Optional;

public interface VignetteSceneRepository extends JpaRepository<VignetteScene, Long> {
    List<VignetteScene> findByUserIdOrderByUpdatedAtDesc(Long userId);
    Optional<VignetteScene> findByIdAndUserId(Long id, Long userId);
}
