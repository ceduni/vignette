package org.titiplex.service;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.CollaboratorRole;
import org.titiplex.persistence.model.ReviewStatus;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
import org.titiplex.persistence.model.ScenarioVisibilityStatus;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.titiplex.util.AuthUtils.*;

/**
 * Single source of truth for who may view, edit, publish or delete a scenario
 * (and, through their scenario, its thumbnails and audios).
 * <p>
 * Rules:
 * <ul>
 *   <li><b>view</b>: published scenarios for everyone; otherwise admin, author, accepted
 *       collaborators, users with an invitation that has not expired, and the original
 *       author of a fork;</li>
 *   <li><b>edit</b>: admin, author, accepted OWNER/EDITOR collaborators. Editing an approved
 *       fork sends it back to review;</li>
 *   <li><b>publish / delete</b>: admin and author only.</li>
 * </ul>
 * A scenario the user may not view is reported as "not found" so its existence is not revealed.
 */
@Service
public class ScenarioAccessService {

    private final ScenarioRepository scenarioRepository;
    private final UserService userService;
    private final ScenarioCollaboratorRepository collaboratorRepository;
    private final NotificationService notificationService;

    public ScenarioAccessService(
            ScenarioRepository scenarioRepository,
            UserService userService,
            ScenarioCollaboratorRepository collaboratorRepository,
            NotificationService notificationService
    ) {
        this.scenarioRepository = scenarioRepository;
        this.userService = userService;
        this.collaboratorRepository = collaboratorRepository;
        this.notificationService = notificationService;
    }

    // ── Questions (true / false) ───────────────────────────────────────

    public boolean isAuthor(Long scenarioId, String username) {
        return username != null && scenarioRepository.existsByIdAndAuthorUsername(scenarioId, username);
    }

    /** Author or accepted OWNER/EDITOR collaborator (admins are handled by the assert methods). */
    public boolean hasEditAccess(Long scenarioId, String username) {
        if (username == null) return false;
        if (isAuthor(scenarioId, username)) return true;
        return collaboration(scenarioId, username)
                .filter(c -> c.getStatus() == CollaborationStatus.ACCEPTED)
                .filter(c -> c.getRole() == CollaboratorRole.OWNER || c.getRole() == CollaboratorRole.EDITOR)
                .isPresent();
    }

    // ── Checks (throw when not allowed) ────────────────────────────────

    public void assertCanViewScenario(Scenario scenario, Authentication authentication) {
        boolean publiclyVisible = scenario.getVisibilityStatus() == ScenarioVisibilityStatus.PUBLISHED
                && scenario.getReviewStatus() != ReviewStatus.PENDING
                && scenario.getReviewStatus() != ReviewStatus.REJECTED;
        if (publiclyVisible || isAdmin(authentication)) {
            return;
        }

        String username = authenticatedUsername(authentication);
        if (username != null) {
            if (isAuthor(scenario.getId(), username)) return;
            // Accepted collaborators, and invited users previewing before they answer
            if (collaboration(scenario.getId(), username)
                    .filter(c -> c.getStatus() == CollaborationStatus.ACCEPTED || isOpenInvitation(c))
                    .isPresent()) return;
            // The original author can view a pending/rejected fork of their own scenario
            if (isAuthorOfForkedOriginal(scenario, username)) return;
        }

        throw new NoSuchElementException("Scenario not found");
    }

    /**
     * Call right before changing a scenario or its media. Besides checking the permission,
     * this sends an approved fork back to review: see {@link #sendApprovedForkBackToReview}.
     */
    public void assertCanEditScenario(Scenario scenario, Authentication authentication) {
        requireAuthenticated(authentication);
        if (isAdmin(authentication)) {
            return;
        }
        String username = authenticatedUsername(authentication);
        if (!hasEditAccess(scenario.getId(), username)) {
            throw new AccessDeniedException("You are not allowed to edit this scenario");
        }
        sendApprovedForkBackToReview(scenario, username);
    }

    /** {@link #assertCanEditScenario(Scenario, Authentication)} from a scenario id (thumbnails, audios). */
    public void assertCanEditScenarioById(Long scenarioId, Authentication authentication) {
        Scenario scenario = scenarioRepository.findById(scenarioId)
                .orElseThrow(() -> new NoSuchElementException("Scenario not found"));
        assertCanEditScenario(scenario, authentication);
    }

    public void assertCanPublishScenario(Scenario scenario, Authentication authentication) {
        requireAuthenticated(authentication);
        if (isAdmin(authentication) || isAuthor(scenario.getId(), authenticatedUsername(authentication))) {
            return;
        }
        throw new AccessDeniedException("Seul l'auteur original peut publier ce scénario.");
    }

    public void assertCanDeleteScenario(Scenario scenario, Authentication authentication) {
        requireAuthenticated(authentication);
        if (isAdmin(authentication) || isAuthor(scenario.getId(), authenticatedUsername(authentication))) {
            return;
        }
        throw new AccessDeniedException("Only the scenario's author can delete it");
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Optional<ScenarioCollaborator> collaboration(Long scenarioId, String username) {
        User user = userService.getUserByUsername(username);
        if (user == null) return Optional.empty();
        return collaboratorRepository.findByScenarioIdAndUserId(scenarioId, user.getId());
    }

    /** A pending invitation that has not expired yet (no expiry date means it never expires). */
    private static boolean isOpenInvitation(ScenarioCollaborator c) {
        return c.getStatus() == CollaborationStatus.PENDING
                && (c.getExpiresAt() == null || c.getExpiresAt().isAfter(Instant.now()));
    }

    /**
     * The original author approved the fork as it was at review time. Any later edit voids
     * that approval: the fork goes back to PENDING (and to DRAFT if it was already published)
     * and the original author is asked to review it again. Otherwise the forker could get an
     * untouched copy approved, then change everything and publish it.
     */
    private void sendApprovedForkBackToReview(Scenario fork, String editorUsername) {
        if (fork.getReviewStatus() != ReviewStatus.APPROVED || fork.getParentScenarioId() == null) {
            return;
        }
        Scenario original = scenarioRepository.findById(fork.getParentScenarioId()).orElse(null);
        User editor = userService.getUserByUsername(editorUsername);
        // No one left to review it again, or the reviewer is the one editing.
        if (original == null || editor == null || editor.getId().equals(original.getAuthor_id())) {
            return;
        }

        fork.setReviewStatus(ReviewStatus.PENDING);
        fork.setReviewedById(null);
        fork.setReviewedAt(null);
        fork.setReviewComment(null);
        if (fork.getVisibilityStatus() == ScenarioVisibilityStatus.PUBLISHED) {
            fork.setVisibilityStatus(ScenarioVisibilityStatus.DRAFT);
            fork.setPublishedAt(null);
        }
        scenarioRepository.save(fork);
        notificationService.notifyForkChangedAfterApproval(original.getAuthor_id(), fork, original, editor.getId());
    }

    private boolean isAuthorOfForkedOriginal(Scenario scenario, String username) {
        if (scenario.getParentScenarioId() == null) return false;
        return scenarioRepository.findById(scenario.getParentScenarioId())
                .map(original -> username.equals(userService.getUserById(original.getAuthor_id()).getUsername()))
                .orElse(false);
    }
}
