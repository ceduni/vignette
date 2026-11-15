package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.titiplex.persistence.model.GlottologPipelineState;

public interface GlottologPipelineStateRepository extends JpaRepository<GlottologPipelineState, Long> {
}
