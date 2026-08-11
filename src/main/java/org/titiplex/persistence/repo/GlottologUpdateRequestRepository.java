package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.GlottologUpdateRequest;
import org.titiplex.persistence.model.GlottologUpdateRequestStatus;

import java.util.List;
import java.util.Optional;

public interface GlottologUpdateRequestRepository extends JpaRepository<GlottologUpdateRequest, Long> {

    Optional<GlottologUpdateRequest> findFirstByStatusInOrderByRequestedAtAsc(
            List<GlottologUpdateRequestStatus> statuses
    );

    List<GlottologUpdateRequest> findTop20ByOrderByRequestedAtDesc();
}
