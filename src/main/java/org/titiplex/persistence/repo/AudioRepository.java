package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.Audio;
import org.titiplex.persistence.model.AudioScope;

import java.util.List;

public interface AudioRepository extends JpaRepository<Audio, Long> {
    List<Audio> findByThumbnailIdAndScopeOrderByIdxAsc(Long thumbnailId, AudioScope scope);

    default List<Audio> findByThumbnailIdOrderByIdxAsc(Long thumbnailId) {
        return findByThumbnailIdAndScopeOrderByIdxAsc(thumbnailId, AudioScope.SCENE);
    }

    boolean existsByThumbnailIdAndIdxAndScope(Long thumbnailId, Integer idx, AudioScope scope);

    boolean existsByStoragePathAndIdNot(String storagePath, Long id);

    default boolean existsByThumbnailIdAndIdx(Long thumbnailId, Integer idx) {
        return existsByThumbnailIdAndIdxAndScope(thumbnailId, idx, AudioScope.SCENE);
    }

    @Query("select coalesce(max(a.idx), 0) from Audio a where a.thumbnailId = :thumbId and a.scope = org.titiplex.persistence.model.AudioScope.SCENE")
    int maxIdx(@Param("thumbId") Long thumbId);

    @Query("select coalesce(max(a.idx), 0) from Audio a where a.scenarioId = :scenarioId and a.scope = org.titiplex.persistence.model.AudioScope.BACKGROUND")
    int maxBackgroundIdx(@Param("scenarioId") Long scenarioId);

    List<Audio> findByScenarioIdAndScopeOrderByIdxAscIdAsc(Long scenarioId, AudioScope scope);

    List<Audio> findAllByLanguageId(String languageId);

    @Query("""
            select a
            from Audio a
            where a.languageId = :languageId
              and a.scope = org.titiplex.persistence.model.AudioScope.SCENE
              and exists (
                    select 1
                    from Scenario s
                    where s.id = a.scenarioId
                      and s.visibilityStatus = org.titiplex.persistence.model.ScenarioVisibilityStatus.PUBLISHED
              )
            order by a.id desc
            """)
    List<Audio> findAllPublishedByLanguageId(@Param("languageId") String languageId);
}
