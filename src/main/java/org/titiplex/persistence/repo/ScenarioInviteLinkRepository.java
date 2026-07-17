package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.ScenarioInviteLink;

import java.util.List;
import java.util.Optional;

public interface ScenarioInviteLinkRepository extends JpaRepository<ScenarioInviteLink, Long> {

    Optional<ScenarioInviteLink> findByToken(String token);

    List<ScenarioInviteLink> findByScenarioId(Long scenarioId);

    List<ScenarioInviteLink> findByScenarioIdAndActiveTrue(Long scenarioId);
}
