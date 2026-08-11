package org.titiplex.persistence.repo;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ScenarioRepository extends JpaRepository<Scenario, Long> {
    Scenario findByTitle(String title);

    boolean existsByIdAndAuthorUsername(Long scenarioId, String username);

    boolean existsByTitle(String title);

    boolean existsByTitleAndAuthorUsernameAndLanguageId(String title, String authorUsername, String languageId);

    List<Scenario> findAllByOrderByCreatedAtDesc();

    List<Scenario> findAllByVisibilityStatusOrderByCreatedAtDesc(ScenarioVisibilityStatus visibilityStatus);

    List<Scenario> findAllByAuthorUsernameOrderByCreatedAtDesc(String username);

    long countByVisibilityStatus(ScenarioVisibilityStatus visibilityStatus);

    @Query("""
                select s
                from Scenario s
                where s.visibilityStatus = org.titiplex.persistence.model.ScenarioVisibilityStatus.PUBLISHED
                   or lower(s.author.username) = lower(:username)
                order by s.createdAt desc
    """)
    List<Scenario> findVisibleToUsername(@Param("username") String username);

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
                select s
                from Scenario s
                order by s.createdAt desc
    """)
    List<Scenario> findAllWithTagsOrderByCreatedAtDesc();

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
                select s
                from Scenario s
                where s.visibilityStatus = :visibilityStatus
                order by s.createdAt desc
    """)
    List<Scenario> findAllByVisibilityStatusWithTagsOrderByCreatedAtDesc(
            @Param("visibilityStatus") ScenarioVisibilityStatus visibilityStatus
    );

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
                select s
                from Scenario s
                where lower(s.author.username) = lower(:username)
                order by s.createdAt desc
    """)
    List<Scenario> findAllByAuthorUsernameWithTagsOrderByCreatedAtDesc(@Param("username") String username);

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
                select s
                from Scenario s
                where s.visibilityStatus = org.titiplex.persistence.model.ScenarioVisibilityStatus.PUBLISHED
                   or lower(s.author.username) = lower(:username)
                order by s.createdAt desc
    """)
    List<Scenario> findVisibleToUsernameWithTags(@Param("username") String username);

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
                select s
                from Scenario s
                where s.language_id = :languageId
                order by s.createdAt desc
    """)
    List<Scenario> findAllByLanguageIdWithTagsOrderByCreatedAtDesc(@Param("languageId") String languageId);

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
                select s
                from Scenario s
                where s.id = :id
    """)
    Optional<Scenario> findByIdWithTags(@Param("id") Long id);

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
                select s
                from Scenario s
                where s.id in :ids
                order by s.createdAt desc
    """)
    List<Scenario> findAllByIdInWithTags(@Param("ids") List<Long> ids);

    @Query("""
                select s
                from Scenario s
                where s.language_id = :languageId
                  and s.visibilityStatus = org.titiplex.persistence.model.ScenarioVisibilityStatus.PUBLISHED
                order by s.createdAt asc, s.id asc
    """)
    List<Scenario> findPublishedByLanguageIdOrderByCreatedAtAscIdAsc(@Param("languageId") String languageId);

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
            select s
            from Scenario s
            join s.language l
            where l.familyId = :familyId
              and s.visibilityStatus = org.titiplex.persistence.model.ScenarioVisibilityStatus.PUBLISHED
            order by s.createdAt desc, s.id desc
            """)
    List<Scenario> findPublishedByFamilyIdOrderByCreatedAtDesc(
            @Param("familyId") String familyId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"tags"})
    @Query("""
            select s
            from Scenario s
            where s.language_id in :languageIds
              and s.visibilityStatus = org.titiplex.persistence.model.ScenarioVisibilityStatus.PUBLISHED
            order by s.createdAt desc, s.id desc
            """)
    List<Scenario> findPublishedByLanguageIdInOrderByCreatedAtDesc(
            @Param("languageIds") Collection<String> languageIds,
            Pageable pageable
    );
}
