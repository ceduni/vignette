package org.titiplex.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.titiplex.persistence.model.CollaborationStatus;
import org.titiplex.persistence.model.DiscussionMessage;
import org.titiplex.persistence.model.DiscussionTargetType;
import org.titiplex.persistence.model.LanguageFollow;
import org.titiplex.persistence.model.Notification;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.ScenarioCollaborator;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.NotificationRepository;
import org.titiplex.persistence.repo.ScenarioCollaboratorRepository;
import org.titiplex.persistence.repo.ScenarioRepository;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.titiplex.security.AuthUtils.requireAuthenticated;

/**
 * Decides who gets notified of what, stores the notifications, and lets users read and
 * clear their own. Live delivery to connected browsers is delegated to {@link NotificationStream}.
 * <p>
 * Notifying is always a side effect of another action (publishing, inviting, commenting…):
 * the {@code notify…} methods log their failures instead of throwing, so they can never
 * make that action fail.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private static final String TYPE_NEW_SCENARIO = "NEW_SCENARIO_IN_FOLLOWED_LANGUAGE";
    private static final String TYPE_FORK_REVIEW_REQUESTED = "FORK_REVIEW_REQUESTED";
    private static final String TYPE_FORK_APPROVED = "FORK_APPROVED";
    private static final String TYPE_FORK_REJECTED = "FORK_REJECTED";
    private static final String TYPE_COLLABORATION_INVITE = "COLLABORATION_INVITE";
    private static final String TYPE_COLLABORATION_ACCEPTED = "COLLABORATION_ACCEPTED";
    private static final String TYPE_COMMENT_REPLY = "COMMENT_REPLY";
    private static final String TYPE_NEW_COMMENT_ON_SCENARIO = "NEW_COMMENT_ON_SCENARIO";

    private final NotificationRepository repo;
    private final NotificationStream stream;
    private final UserService userService;
    private final LanguageFollowService languageFollowService;
    private final LanguageService languageService;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioCollaboratorRepository scenarioCollaboratorRepository;

    public NotificationService(
            NotificationRepository repo,
            NotificationStream stream,
            UserService userService,
            LanguageFollowService languageFollowService,
            LanguageService languageService,
            ScenarioRepository scenarioRepository,
            ScenarioCollaboratorRepository scenarioCollaboratorRepository
    ) {
        this.repo = repo;
        this.stream = stream;
        this.userService = userService;
        this.languageFollowService = languageFollowService;
        this.languageService = languageService;
        this.scenarioRepository = scenarioRepository;
        this.scenarioCollaboratorRepository = scenarioCollaboratorRepository;
    }

    // ── Reading and clearing one's own notifications ───────────────────────

    /** Opens the live stream on which the user's new notifications are pushed. */
    public SseEmitter subscribe(Authentication authentication) {
        return stream.open(currentUser(authentication).getId());
    }

    public List<Map<String, Object>> getNotifications(Authentication authentication) {
        return repo.findAllByUserIdOrderByCreatedAtDesc(currentUser(authentication).getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void markAsRead(Long notificationId, Authentication authentication) {
        User user = currentUser(authentication);
        repo.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                n.setRead(true);
                repo.save(n);
            }
        });
    }

    @Transactional
    public void markAllAsRead(Authentication authentication) {
        repo.markAllReadByUserId(currentUser(authentication).getId());
    }

    @Transactional
    public void deleteNotification(Long notificationId, Authentication authentication) {
        User user = currentUser(authentication);
        repo.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                repo.delete(n);
            }
        });
    }

    @Transactional
    public void deleteAllNotifications(Authentication authentication) {
        repo.deleteAllByUserId(currentUser(authentication).getId());
    }

    public Map<String, Object> toDto(Notification n) {
        return Map.of(
                "id", n.getId(),
                "type", n.getType(),
                "message", n.getMessage(),
                "targetUrl", n.getTargetUrl() != null ? n.getTargetUrl() : "",
                "referenceId", n.getReferenceId() != null ? n.getReferenceId() : 0L,
                "read", n.isRead(),
                "createdAt", n.getCreatedAt().toString()
        );
    }

    // ── Scenario publication ───────────────────────────────────────────────

    /** A scenario was published: notifies everyone following its language, except its author. */
    @Transactional
    public void notifyNewScenarioInLanguage(Scenario scenario) {
        quietly("notifications for scenario " + scenario.getId(), () -> {
            String languageId = scenario.getLanguage_id();
            if (languageId == null) return;

            String message = "New scenario in " + languageName(languageId) + ": " + scenario.getTitle();
            for (LanguageFollow follow : languageFollowService.getFollowers(languageId)) {
                User follower = follow.getUser();
                if (follower.getId().equals(scenario.getAuthor_id())) continue;
                // Publishing again must not notify the same follower twice, even once read.
                if (repo.existsByUserIdAndTypeAndReferenceId(follower.getId(), TYPE_NEW_SCENARIO, scenario.getId())) {
                    continue;
                }
                saveAndPush(notification(follower, TYPE_NEW_SCENARIO, message, "/languages/" + languageId, scenario.getId()));
            }
        });
    }

    // ── Fork review ────────────────────────────────────────────────────────

    /** Someone copied a scenario: its original author must approve the copy before it can be published. */
    @Transactional
    public void notifyForkReviewRequested(Long originalAuthorId, Scenario fork, Scenario original, Long forkerId) {
        quietly("fork review requested notification for scenario " + fork.getId(), () -> send(
                userService.getUserById(originalAuthorId),
                TYPE_FORK_REVIEW_REQUESTED,
                usernameOf(forkerId) + " made a copy of \"" + original.getTitle()
                        + "\" that needs your approval before it can be published",
                "/scenarios/" + fork.getId(),
                fork.getId()
        ));
    }

    /**
     * An approved fork was edited: the approval no longer covers its content, so the
     * original author is asked to review it again.
     */
    @Transactional
    public void notifyForkChangedAfterApproval(Long originalAuthorId, Scenario fork, Scenario original, Long editorId) {
        quietly("fork re-review notification for scenario " + fork.getId(), () -> send(
                userService.getUserById(originalAuthorId),
                TYPE_FORK_REVIEW_REQUESTED,
                usernameOf(editorId) + " changed the copy of \"" + original.getTitle()
                        + "\" you approved; it needs your approval again before it can be published",
                "/scenarios/" + fork.getId(),
                fork.getId()
        ));
    }

    /** The original author approved or rejected a fork: tells the person who made it. */
    @Transactional
    public void notifyForkReviewed(Long forkerId, Scenario fork, boolean approved) {
        quietly("fork review outcome notification for scenario " + fork.getId(), () -> send(
                userService.getUserById(forkerId),
                approved ? TYPE_FORK_APPROVED : TYPE_FORK_REJECTED,
                approved
                        ? "Your copy \"" + fork.getTitle() + "\" was approved and can now be published"
                        : "Your copy \"" + fork.getTitle() + "\" was not approved for publication",
                "/scenarios/" + fork.getId(),
                fork.getId()
        ));
    }

    // ── Collaboration ──────────────────────────────────────────────────────

    /** A user was invited by username (not through an invite link) to collaborate on a scenario. */
    @Transactional
    public void notifyCollaborationInvite(Long invitedUserId, Scenario scenario, Long inviterId, String role) {
        quietly("collaboration invite notification for scenario " + scenario.getId(), () -> send(
                userService.getUserById(invitedUserId),
                TYPE_COLLABORATION_INVITE,
                usernameOf(inviterId) + " invited you to collaborate on \"" + scenario.getTitle()
                        + "\" as " + role.toLowerCase(),
                "/invitations",
                scenario.getId()
        ));
    }

    /** An invited user accepted: tells the scenario owner. */
    @Transactional
    public void notifyInviteAccepted(Long ownerId, Scenario scenario, Long acceptedUserId) {
        quietly("invite accepted notification for scenario " + scenario.getId(), () -> send(
                userService.getUserById(ownerId),
                TYPE_COLLABORATION_ACCEPTED,
                usernameOf(acceptedUserId) + " accepted your invitation to collaborate on \"" + scenario.getTitle() + "\"",
                "/scenarios/" + scenario.getId(),
                scenario.getId()
        ));
    }

    // ── Discussions ────────────────────────────────────────────────────────

    /**
     * A discussion message was posted. Notifies, in order of specificity:
     * <ul>
     *   <li>the parent message's author, if this is a reply to their comment;</li>
     *   <li>for scenario discussions, the scenario's author and accepted collaborators.</li>
     * </ul>
     * Nobody is notified twice for the same message, and never about their own message.
     */
    @Transactional
    public void notifyDiscussionMessage(DiscussionMessage message, DiscussionMessage parent) {
        quietly("discussion notifications for message " + message.getId(), () -> {
            String authorUsername = usernameOf(message.getAuthorId());
            Set<Long> alreadyNotified = new HashSet<>();
            alreadyNotified.add(message.getAuthorId());

            if (parent != null && !alreadyNotified.contains(parent.getAuthorId())) {
                User parentAuthor = userService.getUserById(parent.getAuthorId());
                send(
                        parentAuthor,
                        TYPE_COMMENT_REPLY,
                        authorUsername + " replied to your comment",
                        withDiscussionHighlight(
                                discussionTargetUrl(message.getTargetType(), message.getTargetId()), message.getId()),
                        message.getId()
                );
                alreadyNotified.add(parentAuthor.getId());
            }

            if (message.getTargetType() == DiscussionTargetType.SCENARIO) {
                notifyScenarioTeamOfComment(message, authorUsername, alreadyNotified);
            }
        });
    }

    private void notifyScenarioTeamOfComment(DiscussionMessage message, String authorUsername, Set<Long> alreadyNotified) {
        Long scenarioId = Long.parseLong(message.getTargetId());
        Scenario scenario = scenarioRepository.findById(scenarioId).orElse(null);
        if (scenario == null) return;

        Set<Long> recipientIds = new HashSet<>();
        recipientIds.add(scenario.getAuthor_id());
        for (ScenarioCollaborator collaborator : scenarioCollaboratorRepository
                .findByScenarioIdAndStatus(scenarioId, CollaborationStatus.ACCEPTED)) {
            recipientIds.add(collaborator.getUserId());
        }
        recipientIds.removeAll(alreadyNotified);

        for (Long recipientId : recipientIds) {
            send(
                    userService.getUserById(recipientId),
                    TYPE_NEW_COMMENT_ON_SCENARIO,
                    authorUsername + " commented on \"" + scenario.getTitle() + "\"",
                    withDiscussionHighlight("/scenarios/" + scenarioId, message.getId()),
                    message.getId()
            );
        }
    }

    private String discussionTargetUrl(DiscussionTargetType targetType, String targetId) {
        return switch (targetType) {
            case SCENARIO -> "/scenarios/" + targetId;
            case LANGUAGE -> "/languages/" + targetId;
            case AUDIO -> "";
        };
    }

    /**
     * Appends the replied/commented-on message's id as a query param so the
     * scenario page can auto-open its discussion section and scroll to it.
     */
    private String withDiscussionHighlight(String url, Long messageId) {
        if (url == null || url.isBlank()) return url;
        return url + (url.contains("?") ? "&" : "?") + "discussion=" + messageId;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private User currentUser(Authentication authentication) {
        requireAuthenticated(authentication);
        return userService.getUserByUsername(authentication.getName());
    }

    private String usernameOf(Long userId) {
        return userService.getUserById(userId).getUsername();
    }

    /** The language's name, or its id when the language cannot be loaded. */
    private String languageName(String languageId) {
        try {
            return languageService.getLanguage(languageId).getName();
        } catch (Exception e) {
            return languageId;
        }
    }

    /** Runs a notification step; a failure is logged and never reaches the caller. */
    private void quietly(String what, Runnable step) {
        try {
            step.run();
        } catch (Exception e) {
            log.error("Failed to send {}: {}", what, e.getMessage());
        }
    }

    /**
     * Notifies the recipient, unless the exact same notification (same recipient, type,
     * reference and message) is still waiting unread: a repeated action (double click,
     * re-sent invitation) then does not pile up identical notifications. Once the user has
     * read it, a new occurrence is notified again (e.g. a re-invitation after a decline).
     */
    private void send(User recipient, String type, String message, String targetUrl, Long referenceId) {
        if (repo.existsByUserIdAndTypeAndReferenceIdAndMessageAndReadFalse(recipient.getId(), type, referenceId, message)) {
            return;
        }
        saveAndPush(notification(recipient, type, message, targetUrl, referenceId));
    }

    private void saveAndPush(Notification notification) {
        repo.save(notification);
        stream.push(notification.getUser().getId(), () -> toDto(notification));
    }

    private static Notification notification(User recipient, String type, String message, String targetUrl, Long referenceId) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setType(type);
        notification.setMessage(message);
        notification.setTargetUrl(targetUrl);
        notification.setReferenceId(referenceId);
        return notification;
    }
}
