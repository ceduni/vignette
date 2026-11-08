package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.Thumbnail;

import java.util.List;

public interface ThumbnailRepository extends JpaRepository<Thumbnail, Long> {

    /**
     * Locks the thumbnail row until the end of the current transaction. Used to serialize
     * "next idx = max(idx) + 1" computations for the audios of this thumbnail.
     */
    @Query(value = "SELECT id FROM thumbnail WHERE id = :id FOR UPDATE", nativeQuery = true)
    Long lockById(@Param("id") Long id);
    List<Thumbnail> findByTitle(String title);

    List<Thumbnail> getThumbnailsByScenarioId(Long scenarioId);

    List<Thumbnail> findByScenarioIdOrderByIdxAsc(Long scenarioId);

    boolean existsByStoragePathAndIdNot(String storagePath, Long id);

    boolean existsByScenarioIdAndImageSha256(Long scenarioId, String imageSha256);

    @Query("select coalesce(max(t.idx), -1) from Thumbnail t where t.scenarioId = :scenarioId")
    int maxIdx(@Param("scenarioId") Long scenarioId);
}
