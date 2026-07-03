package org.titiplex.persistence.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.titiplex.persistence.model.LanguageFollow;

import java.util.List;
import java.util.Optional;

public interface LanguageFollowRepository extends JpaRepository<LanguageFollow, Long> {

    Optional<LanguageFollow> findByLanguageIdAndUserId(String languageId, Long userId);

    boolean existsByLanguageIdAndUserId(String languageId, Long userId);

    List<LanguageFollow> findAllByUserId(Long userId);

    // Used by NotificationService: get all followers of a given language
    @Query("SELECT lf FROM LanguageFollow lf JOIN FETCH lf.user WHERE lf.language.id = :languageId")
    List<LanguageFollow> findAllByLanguageIdWithUser(@Param("languageId") String languageId);

    long countByLanguageId(String languageId);

    void deleteByLanguageIdAndUserId(String languageId, Long userId);
}