package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioLike;
import org.titiplex.persistence.model.User;

import java.util.List;

public interface ScenarioLikeRepository extends JpaRepository<ScenarioLike, Long> {
    boolean existsByScenarioAndUser(Scenario scenario, User user);
    void deleteByScenarioAndUser(Scenario scenario, User user);
    void deleteByScenario_Id(Long scenarioId);
    long countByScenario(Scenario scenario);

    @Query("select l.scenario.id from ScenarioLike l where l.user = :user")
    List<Long> findScenarioIdsByUser(@Param("user") User user);
}
