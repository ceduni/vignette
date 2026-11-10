package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.titiplex.persistence.model.ScenarioTag;

import java.util.List;
import java.util.Optional;

public interface ScenarioTagRepository extends JpaRepository<ScenarioTag, Long> {

    Optional<ScenarioTag> findByNormalizedName(String normalizedName);

    @Query("""
            select t
            from ScenarioTag t
            where lower(t.name) like lower(concat('%', :query, '%'))
               or lower(t.normalizedName) like lower(concat('%', :query, '%'))
            order by t.name asc
            """)
    List<ScenarioTag> searchSuggestions(String query);
}