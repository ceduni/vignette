package org.titiplex.persistence.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.api.dto.LanguageOptionDto;
import org.titiplex.persistence.model.Language;
import org.titiplex.service.GlottologLanguageSnapshot;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface LanguageRepository extends JpaRepository<Language, String> {
    @EntityGraph(attributePaths = {"family", "parent"})
    Page<Language> findAll(Pageable pageable);

    @EntityGraph(attributePaths = {"family", "parent"})
    Optional<Language> findWithFamilyAndParentById(String id);

    @EntityGraph(attributePaths = {"family", "parent"})
    @Query("select l from Language l")
    List<Language> findAllWithFamilyAndParent();

    @Query("select new org.titiplex.api.dto.LanguageOptionDto(l.id, l.name) " +
            "from Language l " +
            "where lower(l.level) <> 'family' " +
            "and (:hasQuery = false or lower(l.name) like :pattern) " +
            "order by l.name")
    Page<LanguageOptionDto> listOptions(
            @Param("hasQuery") boolean hasQuery,
            @Param("pattern") String pattern,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"family", "parent"})
    @Query("""
            select l from Language l
            left join l.family f
            left join l.parent p
            where lower(l.level) <> 'family'
              and (:hasQuery = false
               or lower(l.id) like :pattern
               or lower(l.name) like :pattern
               or lower(coalesce(f.name, '')) like :pattern
               or lower(coalesce(p.name, '')) like :pattern)
            """)
    Page<Language> search(
            @Param("hasQuery") boolean hasQuery,
            @Param("pattern") String pattern,
            Pageable pageable
    );

    List<Language> findAllByFamilyId(String familyId);

    List<Language> findAllByIdIn(Collection<String> ids);

    @Query("select l from Language l where lower(l.level) = 'family'")
    List<Language> findAllFamilies();

    @Query("""
            select l from Language l
            where l.familyId in :ids or l.parentId in :ids
            """)
    List<Language> findAllReferencingIds(@Param("ids") Collection<String> ids);

    @Query("""
            select new org.titiplex.service.GlottologLanguageSnapshot(
                l.id,
                l.name,
                l.familyId,
                l.parentId,
                l.bookkeeping,
                l.level,
                l.latitude,
                l.longitude,
                l.iso639P3code,
                l.description,
                l.markupDescription,
                l.childFamilyCount,
                l.childLanguageCount,
                l.childDialectCount,
                l.countryIds
            )
            from Language l
            where l.id in :ids
            """)
    List<GlottologLanguageSnapshot> findGlottologSnapshotsByIdIn(@Param("ids") Collection<String> ids);

    Optional<Language> findByName(String name);
}
