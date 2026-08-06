package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.ScenarioHistoryEntry;

import java.util.List;

public interface ScenarioHistoryRepository extends JpaRepository<ScenarioHistoryEntry, Long> {
    List<ScenarioHistoryEntry> findByScenarioIdOrderByCreatedAtDesc(Long scenarioId);
}
