package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.GlottologUpdateHistory;
import org.titiplex.persistence.model.GlottologUpdateHistoryStatus;

import java.util.List;
import java.util.Optional;

public interface GlottologUpdateHistoryRepository extends JpaRepository<GlottologUpdateHistory, Long> {
    List<GlottologUpdateHistory> findTop50ByOrderByStartedAtDesc();

    Optional<GlottologUpdateHistory> findTop1ByStatusOrderByFinishedAtDesc(GlottologUpdateHistoryStatus status);
}
