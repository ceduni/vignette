package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.GlottologAdminSettings;

public interface GlottologAdminSettingsRepository extends JpaRepository<GlottologAdminSettings, Long> {
}
