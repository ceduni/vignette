package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioBookmark;
import org.titiplex.persistence.model.User;

public interface ScenarioBookmarkRepository extends JpaRepository<ScenarioBookmark, Long> {
    boolean existsByScenarioAndUser(Scenario scenario, User user);
    void deleteByScenarioAndUser(Scenario scenario, User user);
    long countByScenario(Scenario scenario);
    void deleteByScenario_Id(Long scenarioId);
}
