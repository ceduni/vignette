package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.ScenarioCollaborator;

import java.util.List;
import java.util.Optional;

public interface ScenarioCollaboratorRepository extends JpaRepository<ScenarioCollaborator, Long> {

    List<ScenarioCollaborator> findByScenarioId(Long scenarioId);

    List<ScenarioCollaborator> findByScenarioIdAndStatus(Long scenarioId, CollaborationStatus status);

    Optional<ScenarioCollaborator> findByScenarioIdAndUserId(Long scenarioId, Long userId);

    boolean existsByScenarioIdAndUserId(Long scenarioId, Long userId);

    List<ScenarioCollaborator> findByUserIdAndStatus(Long userId, CollaborationStatus status);

    void deleteByScenarioIdAndUserId(Long scenarioId, Long userId);

    void deleteByScenarioId(Long scenarioId);
}
