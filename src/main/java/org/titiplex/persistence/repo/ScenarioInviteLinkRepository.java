package org.titiplex.persistence.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.ScenarioInviteLink;

import java.util.List;
import java.util.Optional;

public interface ScenarioInviteLinkRepository extends JpaRepository<ScenarioInviteLink, Long> {

    /**
     * Loads the link and locks its row until the end of the current transaction, so that
     * concurrent joins check and increment useCount one after the other (maxUses is respected).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from ScenarioInviteLink l where l.token = :token")
    Optional<ScenarioInviteLink> findByTokenForUpdate(@Param("token") String token);

    List<ScenarioInviteLink> findByScenarioId(Long scenarioId);

    List<ScenarioInviteLink> findByScenarioIdAndActiveTrue(Long scenarioId);

    void deleteByScenarioId(Long scenarioId);
}
