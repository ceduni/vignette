package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.GlottologNotification;

import java.util.List;

public interface GlottologNotificationRepository extends JpaRepository<GlottologNotification, Long> {
    List<GlottologNotification> findTop30ByOrderByCreatedAtDesc();
}
