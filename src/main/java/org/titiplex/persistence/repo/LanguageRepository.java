package org.titiplex.persistence.repo;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.api.dto.LanguageOptionDto;
import org.titiplex.persistence.model.Language;

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
            "where (:q is null or lower(l.name) like lower(concat('%', :q, '%'))) " +
            "order by l.name")
    Page<LanguageOptionDto> listOptions(@Param("q") String q, Pageable pageable);

    @EntityGraph(attributePaths = {"family", "parent"})
    @Query("""
            select l from Language l
            left join l.family f
            left join l.parent p
            where :q is null
               or lower(l.id) like lower(concat('%', :q, '%'))
               or lower(l.name) like lower(concat('%', :q, '%'))
               or lower(coalesce(f.name, '')) like lower(concat('%', :q, '%'))
               or lower(coalesce(p.name, '')) like lower(concat('%', :q, '%'))
            """)
    Page<Language> search(@Param("q") String q, Pageable pageable);

    List<Language> findAllByFamilyId(String familyId);
}
