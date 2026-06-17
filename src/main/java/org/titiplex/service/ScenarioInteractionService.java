package org.titiplex.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.titiplex.api.dto.ScenarioInteractionStatusDto;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioBookmark;
import org.titiplex.persistence.model.ScenarioLike;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioBookmarkRepository;
import org.titiplex.persistence.repo.ScenarioLikeRepository;
import org.titiplex.persistence.repo.ScenarioRepository;
import org.titiplex.persistence.repo.UserRepository;

@Service
public class ScenarioInteractionService {

    private final ScenarioRepository scenarioRepository;
    private final UserRepository userRepository;
    private final ScenarioLikeRepository likeRepository;
    private final ScenarioBookmarkRepository bookmarkRepository;

    public ScenarioInteractionService(
            ScenarioRepository scenarioRepository,
            UserRepository userRepository,
            ScenarioLikeRepository likeRepository,
            ScenarioBookmarkRepository bookmarkRepository
    ) {
        this.scenarioRepository = scenarioRepository;
        this.userRepository = userRepository;
        this.likeRepository = likeRepository;
        this.bookmarkRepository = bookmarkRepository;
    }

    // ── Likes ──────────────────────────────────────────────────────────────

    @Transactional
    public ScenarioInteractionStatusDto likeScenario(Long scenarioId, String username) {
        Scenario scenario = getScenario(scenarioId);
        User user = getUser(username);

        if (!likeRepository.existsByScenarioAndUser(scenario, user)) {
            ScenarioLike like = new ScenarioLike();
            like.setScenario(scenario);
            like.setUser(user);
            likeRepository.save(like);
        }

        return buildStatus(scenario, user);
    }

    @Transactional
    public ScenarioInteractionStatusDto unlikeScenario(Long scenarioId, String username) {
        Scenario scenario = getScenario(scenarioId);
        User user = getUser(username);
        likeRepository.deleteByScenarioAndUser(scenario, user);
        return buildStatus(scenario, user);
    }

    // ── Bookmarks ──────────────────────────────────────────────────────────

    @Transactional
    public ScenarioInteractionStatusDto bookmarkScenario(Long scenarioId, String username) {
        Scenario scenario = getScenario(scenarioId);
        User user = getUser(username);

        if (!bookmarkRepository.existsByScenarioAndUser(scenario, user)) {
            ScenarioBookmark bookmark = new ScenarioBookmark();
            bookmark.setScenario(scenario);
            bookmark.setUser(user);
            bookmarkRepository.save(bookmark);
        }

        return buildStatus(scenario, user);
    }

    @Transactional
    public ScenarioInteractionStatusDto unbookmarkScenario(Long scenarioId, String username) {
        Scenario scenario = getScenario(scenarioId);
        User user = getUser(username);
        bookmarkRepository.deleteByScenarioAndUser(scenario, user);
        return buildStatus(scenario, user);
    }

    // ── Status ─────────────────────────────────────────────────────────────

    public ScenarioInteractionStatusDto getStatus(Long scenarioId, String username) {
        Scenario scenario = getScenario(scenarioId);
        User user = getUser(username);
        return buildStatus(scenario, user);
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private ScenarioInteractionStatusDto buildStatus(Scenario scenario, User user) {
        boolean liked = likeRepository.existsByScenarioAndUser(scenario, user);
        boolean bookmarked = bookmarkRepository.existsByScenarioAndUser(scenario, user);
        long likeCount = likeRepository.countByScenario(scenario);
        return new ScenarioInteractionStatusDto(liked, bookmarked, likeCount);
    }

    private Scenario getScenario(Long id) {
        return scenarioRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scenario not found: " + id));
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
    }
}
