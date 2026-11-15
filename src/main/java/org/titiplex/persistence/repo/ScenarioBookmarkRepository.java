package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioBookmark;
import org.titiplex.persistence.model.User;

import java.util.List;

public interface ScenarioBookmarkRepository extends JpaRepository<ScenarioBookmark, Long> {
    boolean existsByScenarioAndUser(Scenario scenario, User user);
    void deleteByScenarioAndUser(Scenario scenario, User user);
    void deleteByScenario_Id(Long scenarioId);
    long countByScenario(Scenario scenario);

    @Query("select b.scenario.id from ScenarioBookmark b where b.user = :user")
    List<Long> findScenarioIdsByUser(@Param("user") User user);
}
