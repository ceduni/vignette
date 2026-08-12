package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.Thumbnail;

import java.util.List;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {
    List<Thumbnail> findByTitle(String title);

    List<Thumbnail> getThumbnailsByScenarioId(Long scenarioId);

    List<Thumbnail> findByScenarioIdOrderByIdxAsc(Long scenarioId);

    boolean existsByStoragePathAndIdNot(String storagePath, Long id);

    @Query("select coalesce(max(t.idx), -1) from Thumbnail t where t.scenarioId = :scenarioId")
    int maxIdx(@Param("scenarioId") Long scenarioId);
}
