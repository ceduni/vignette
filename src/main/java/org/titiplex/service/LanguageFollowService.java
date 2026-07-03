package org.titiplex.service;

import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.persistence.model.Language;
import org.titiplex.persistence.model.LanguageFollow;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.LanguageFollowRepository;

import java.util.List;

@Service
public class LanguageFollowService {

    private final LanguageFollowRepository repo;
    private final UserService userService;
    private final LanguageService languageService;

    public LanguageFollowService(
            LanguageFollowRepository repo,
            UserService userService,
            LanguageService languageService
    ) {
        this.repo = repo;
        this.userService = userService;
        this.languageService = languageService;
    }

    /** Toggle follow — returns true if now following, false if unfollowed */
    @Transactional
    public boolean toggleFollow(String languageId, Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        Language language = languageService.getLanguage(languageId);

        if (repo.existsByLanguageIdAndUserId(languageId, user.getId())) {
            repo.deleteByLanguageIdAndUserId(languageId, user.getId());
            return false;
        }

        LanguageFollow follow = new LanguageFollow();
        follow.setLanguage(language);
        follow.setUser(user);
        repo.save(follow);
        return true;
    }

    public boolean isFollowing(String languageId, Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) return false;
        User user = userService.getUserByUsername(authentication.getName());
        return repo.existsByLanguageIdAndUserId(languageId, user.getId());
    }

    /** All language IDs followed by the authenticated user */
    public List<String> getFollowedLanguageIds(Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        return repo.findAllByUserId(user.getId())
                .stream()
                .map(f -> f.getLanguage().getId())
                .toList();
    }

    /** All followers of a language — used internally by NotificationService */
    public List<LanguageFollow> getFollowers(String languageId) {
        return repo.findAllByLanguageIdWithUser(languageId);
    }

    public long countFollowers(String languageId) {
        return repo.countByLanguageId(languageId);
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
    }
}