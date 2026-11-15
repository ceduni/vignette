package org.titiplex.service;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.api.dto.MyScenarioInteractionsDto;
import org.titiplex.api.dto.ScenarioInteractionStatusDto;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioBookmark;
import org.titiplex.persistence.model.ScenarioLike;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioBookmarkRepository;
import org.titiplex.persistence.repo.ScenarioLikeRepository;
import org.titiplex.persistence.repo.UserRepository;

@Service
public class ScenarioInteractionService {

    private final ScenarioService scenarioService;
    private final UserRepository userRepository;
    private final ScenarioLikeRepository likeRepository;
    private final ScenarioBookmarkRepository bookmarkRepository;

    public ScenarioInteractionService(
            ScenarioService scenarioService,
            UserRepository userRepository,
            ScenarioLikeRepository likeRepository,
            ScenarioBookmarkRepository bookmarkRepository
    ) {
        this.scenarioService = scenarioService;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    // ── Likes ──────────────────────────────────────────────────────────────

    // Not @Transactional on purpose: the insert runs in its own transaction so that a
    // unique-constraint violation from a concurrent request (double click, two tabs)
    // can be swallowed without poisoning the rest of the request.
    public ScenarioInteractionStatusDto likeScenario(Long scenarioId, Authentication authentication) {
        Scenario scenario = getVisibleScenario(scenarioId, authentication);
        User user = getUser(authentication);

        if (!likeRepository.existsByScenarioAndUser(scenario, user)) {
            ScenarioLike like = new ScenarioLike();
            like.setScenario(scenario);
            like.setUser(user);
            try {
                likeRepository.saveAndFlush(like);
            } catch (DataIntegrityViolationException alreadyLiked) {
                // A concurrent request inserted the same like first: the end state is the one wanted.
            }
        }

        return buildStatus(scenario, user);
    }

    @Transactional
    public ScenarioInteractionStatusDto unlikeScenario(Long scenarioId, Authentication authentication) {
        Scenario scenario = getVisibleScenario(scenarioId, authentication);
        User user = getUser(authentication);
        likeRepository.deleteByScenarioAndUser(scenario, user);
        return buildStatus(scenario, user);
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    // Not @Transactional on purpose, see likeScenario.
    public ScenarioInteractionStatusDto bookmarkScenario(Long scenarioId, Authentication authentication) {
        Scenario scenario = getVisibleScenario(scenarioId, authentication);
        User user = getUser(authentication);

        if (!bookmarkRepository.existsByScenarioAndUser(scenario, user)) {
            ScenarioBookmark bookmark = new ScenarioBookmark();
            bookmark.setScenario(scenario);
            bookmark.setUser(user);
            try {
                bookmarkRepository.saveAndFlush(bookmark);
            } catch (DataIntegrityViolationException alreadyBookmarked) {
                // A concurrent request inserted the same bookmark first: the end state is the one wanted.
            }
        }

        return buildStatus(scenario, user);
    }

    @Transactional
    public ScenarioInteractionStatusDto unbookmarkScenario(Long scenarioId, Authentication authentication) {
        Scenario scenario = getVisibleScenario(scenarioId, authentication);
        User user = getUser(authentication);
        bookmarkRepository.deleteByScenarioAndUser(scenario, user);
        return buildStatus(scenario, user);
    }

    // ── Status ─────────────────────────────────────────────────────────────

    public ScenarioInteractionStatusDto getStatus(Long scenarioId, Authentication authentication) {
        Scenario scenario = getVisibleScenario(scenarioId, authentication);
        User user = getUser(authentication);
        return buildStatus(scenario, user);
    }

    public MyScenarioInteractionsDto getMyInteractions(Authentication authentication) {
        User user = getUser(authentication);
        return new MyScenarioInteractionsDto(
                likeRepository.findScenarioIdsByUser(user),
                bookmarkRepository.findScenarioIdsByUser(user)
        );
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ScenarioInteractionStatusDto buildStatus(Scenario scenario, User user) {
        boolean liked = likeRepository.existsByScenarioAndUser(scenario, user);
        boolean bookmarked = bookmarkRepository.existsByScenarioAndUser(scenario, user);
        long likeCount = likeRepository.countByScenario(scenario);
        return new ScenarioInteractionStatusDto(liked, bookmarked, likeCount);
    }

    /** Same visibility rules as the scenario page: drafts and private scenarios are not reachable. */
    private Scenario getVisibleScenario(Long id, Authentication authentication) {
        return scenarioService.getVisibleScenario(id, authentication);
    }

    private User getUser(Authentication authentication) {
        String username = authentication.getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
