package org.titiplex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.InsufficientAuthenticationException;
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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    private static final String TYPE_NEW_SCENARIO = "NEW_SCENARIO_IN_FOLLOWED_LANGUAGE";
    private static final String TYPE_FORK_REVIEW_REQUESTED = "FORK_REVIEW_REQUESTED";
    private static final String TYPE_FORK_APPROVED = "FORK_APPROVED";
    private static final String TYPE_FORK_REJECTED = "FORK_REJECTED";
    private static final String TYPE_COLLABORATION_INVITE = "COLLABORATION_INVITE";
    private static final String TYPE_COLLABORATION_ACCEPTED = "COLLABORATION_ACCEPTED";
    private static final String TYPE_COMMENT_REPLY = "COMMENT_REPLY";
    private static final String TYPE_NEW_COMMENT_ON_SCENARIO = "NEW_COMMENT_ON_SCENARIO";

    // userId → list of active SSE emitters
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final NotificationRepository repo;
    private final UserService userService;
    private final LanguageFollowService languageFollowService;
    private final LanguageService languageService;
    private final ScenarioRepository scenarioRepository;
    private final ScenarioCollaboratorRepository scenarioCollaboratorRepository;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationRepository repo,
            UserService userService,
            LanguageFollowService languageFollowService,
            LanguageService languageService,
            ScenarioRepository scenarioRepository,
            ScenarioCollaboratorRepository scenarioCollaboratorRepository,
            ObjectMapper objectMapper
    ) {
        this.repo = repo;
        this.userService = userService;
        this.languageFollowService = languageFollowService;
        this.languageService = languageService;
        this.scenarioRepository = scenarioRepository;
        this.scenarioCollaboratorRepository = scenarioCollaboratorRepository;
        this.objectMapper = objectMapper;
    }

    // ── SSE connection management ──────────────────────────────────────────

    public SseEmitter subscribe(Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        Long userId = user.getId();

        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(userId, emitter));
        emitter.onTimeout(()    -> removeEmitter(userId, emitter));
        emitter.onError(e      -> removeEmitter(userId, emitter));

        // Send a keep-alive comment on connect
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            removeEmitter(userId, emitter);
        }

        return emitter;
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) emitters.remove(userId);
        }
    }

    private void pushToUser(Long userId, Notification notification) {
        CopyOnWriteArrayList<SseEmitter> list = emitters.get(userId);
        if (list == null || list.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(toDto(notification));
        } catch (Exception e) {
            return;
        }

        List<SseEmitter> dead = new java.util.ArrayList<>();
        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(json));
            } catch (IOException ex) {
                dead.add(emitter);
            }
        }
        dead.forEach(e -> removeEmitter(userId, e));
    }

    // ── Notification creation ──────────────────────────────────────────────

    /**
     * Called by ScenarioService when a scenario is published.
     * Notifies all followers of the scenario's language.
     */
    @Transactional
    public void notifyNewScenarioInLanguage(Scenario scenario) {
        try {
            String languageId = scenario.getLanguage_id();
            if (languageId == null) return;

            // Load language name fresh — avoids lazy-loading proxy issues
            String languageName;
            try {
                languageName = languageService.getLanguage(languageId).getName();
            } catch (Exception e) {
                languageName = languageId;
            }

            List<LanguageFollow> followers = languageFollowService.getFollowers(languageId);
            Long scenarioId  = scenario.getId();
            Long authorId    = scenario.getAuthor_id();
            String title     = scenario.getTitle();

            for (LanguageFollow follow : followers) {
                User follower = follow.getUser();

                // Don't notify the author themselves
                if (follower.getId().equals(authorId)) continue;

                // Avoid duplicate notifications
                if (repo.existsByUserIdAndTypeAndReferenceId(follower.getId(), TYPE_NEW_SCENARIO, scenarioId)) continue;

                Notification notif = new Notification();
                notif.setUser(follower);
                notif.setType(TYPE_NEW_SCENARIO);
                notif.setMessage("New scenario in " + languageName + ": " + title);
                notif.setTargetUrl("/languages/" + languageId);
                notif.setReferenceId(scenarioId);
                repo.save(notif);

                pushToUser(follower.getId(), notif);
            }
        } catch (Exception e) {
            // Notification failure must never block scenario publishing
            org.slf4j.LoggerFactory.getLogger(NotificationService.class)
                .error("Failed to send notifications for scenario {}: {}", scenario.getId(), e.getMessage());
        }
    }

    /**
     * Called by ScenarioService.forkScenario() when a fork is created by
     * someone other than the original author. Notifies the original author
     * that a review is needed before the fork can be published.
     */
    @Transactional
    public void notifyForkReviewRequested(Long originalAuthorId, Scenario fork, Scenario original, Long forkerId) {
        try {
            User originalAuthor = userService.getUserById(originalAuthorId);
            String forkerUsername = userService.getUserById(forkerId).getUsername();

            Notification notif = new Notification();
            notif.setUser(originalAuthor);
            notif.setType(TYPE_FORK_REVIEW_REQUESTED);
            notif.setMessage(forkerUsername + " made a copy of \"" + original.getTitle() + "\" that needs your approval before it can be published");
            notif.setTargetUrl("/scenarios/" + fork.getId());
            notif.setReferenceId(fork.getId());
            repo.save(notif);

            pushToUser(originalAuthorId, notif);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(NotificationService.class)
                .error("Failed to send fork review requested notification for scenario {}: {}", fork.getId(), e.getMessage());
        }
    }

    /**
     * Called by ScenarioService.reviewFork() once the original author has
     * approved or rejected a fork. Notifies the forker of the outcome.
     */
    @Transactional
    public void notifyForkReviewed(Long forkerId, Scenario fork, boolean approved) {
        try {
            User forker = userService.getUserById(forkerId);

            Notification notif = new Notification();
            notif.setUser(forker);
            notif.setType(approved ? TYPE_FORK_APPROVED : TYPE_FORK_REJECTED);
            notif.setMessage(approved
                    ? "Your copy \"" + fork.getTitle() + "\" was approved and can now be published"
                    : "Your copy \"" + fork.getTitle() + "\" was not approved for publication");
            notif.setTargetUrl("/scenarios/" + fork.getId());
            notif.setReferenceId(fork.getId());
            repo.save(notif);

            pushToUser(forkerId, notif);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(NotificationService.class)
                .error("Failed to send fork review outcome notification for scenario {}: {}", fork.getId(), e.getMessage());
        }
    }

    /**
     * Called by ScenarioCollaborationService when a user is invited to
     * collaborate on a scenario (by username, not via invite link).
     */
    @Transactional
    public void notifyCollaborationInvite(Long invitedUserId, Scenario scenario, Long inviterId, String role) {
        try {
            User invitedUser = userService.getUserById(invitedUserId);
            String inviterUsername = userService.getUserById(inviterId).getUsername();

            Notification notif = new Notification();
            notif.setUser(invitedUser);
            notif.setType(TYPE_COLLABORATION_INVITE);
            notif.setMessage(inviterUsername + " invited you to collaborate on \"" + scenario.getTitle() + "\" as " + role.toLowerCase());
            notif.setTargetUrl("/invitations");
            notif.setReferenceId(scenario.getId());
            repo.save(notif);

            pushToUser(invitedUserId, notif);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(NotificationService.class)
                .error("Failed to send collaboration invite notification for scenario {}: {}", scenario.getId(), e.getMessage());
        }
    }

    /**
     * Called by ScenarioCollaborationService when an invited user accepts.
     * Notifies the scenario owner.
     */
    @Transactional
    public void notifyInviteAccepted(Long ownerId, Scenario scenario, Long acceptedUserId) {
        try {
            User owner = userService.getUserById(ownerId);
            String accepterUsername = userService.getUserById(acceptedUserId).getUsername();

            Notification notif = new Notification();
            notif.setUser(owner);
            notif.setType(TYPE_COLLABORATION_ACCEPTED);
            notif.setMessage(accepterUsername + " accepted your invitation to collaborate on \"" + scenario.getTitle() + "\"");
            notif.setTargetUrl("/scenarios/" + scenario.getId());
            notif.setReferenceId(scenario.getId());
            repo.save(notif);

            pushToUser(ownerId, notif);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(NotificationService.class)
                .error("Failed to send invite accepted notification for scenario {}: {}", scenario.getId(), e.getMessage());
        }
    }

    /**
     * Called by CommunityService.createMessage() whenever a new discussion message is posted.
     * Notifies, in order of specificity:
     * - the parent message's author, if this is a reply to their comment;
     * - for scenario discussions, the scenario's author and accepted collaborators
     *   (skipping anyone already notified above, so a single message never double-pings
     *   the same person).
     * The author of the new message is never notified about their own message.
     */
    @Transactional
    public void notifyDiscussionMessage(DiscussionMessage message, DiscussionMessage parent) {
        try {
            Long authorId = message.getAuthorId();
            User author = userService.getUserById(authorId);
            Set<Long> alreadyNotified = new HashSet<>();
            alreadyNotified.add(authorId);

            if (parent != null && !alreadyNotified.contains(parent.getAuthorId())) {
                User parentAuthor = userService.getUserById(parent.getAuthorId());

                Notification notif = new Notification();
                notif.setUser(parentAuthor);
                notif.setType(TYPE_COMMENT_REPLY);
                notif.setMessage(author.getUsername() + " replied to your comment");
                notif.setTargetUrl(withDiscussionHighlight(
                        discussionTargetUrl(message.getTargetType(), message.getTargetId()), message.getId()));
                notif.setReferenceId(message.getId());
                repo.save(notif);

                pushToUser(parentAuthor.getId(), notif);
                alreadyNotified.add(parentAuthor.getId());
            }

            if (message.getTargetType() == DiscussionTargetType.SCENARIO) {
                Long scenarioId = Long.parseLong(message.getTargetId());
                Scenario scenario = scenarioRepository.findById(scenarioId).orElse(null);
                if (scenario != null) {
                    Set<Long> recipientIds = new HashSet<>();
                    recipientIds.add(scenario.getAuthor_id());
                    for (ScenarioCollaborator collaborator : scenarioCollaboratorRepository
                            .findByScenarioIdAndStatus(scenarioId, CollaborationStatus.ACCEPTED)) {
                        recipientIds.add(collaborator.getUserId());
                    }
                    recipientIds.removeAll(alreadyNotified);

                    for (Long recipientId : recipientIds) {
                        User recipient = userService.getUserById(recipientId);

                        Notification notif = new Notification();
                        notif.setUser(recipient);
                        notif.setType(TYPE_NEW_COMMENT_ON_SCENARIO);
                        notif.setMessage(author.getUsername() + " commented on \"" + scenario.getTitle() + "\"");
                        notif.setTargetUrl(withDiscussionHighlight("/scenarios/" + scenarioId, message.getId()));
                        notif.setReferenceId(message.getId());
                        repo.save(notif);

                        pushToUser(recipientId, notif);
                    }
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(NotificationService.class)
                .error("Failed to send discussion notifications for message {}: {}", message.getId(), e.getMessage());
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

    // ── CRUD ───────────────────────────────────────────────────────────────

    public List<Map<String, Object>> getNotifications(Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        return repo.findAllByUserIdOrderByCreatedAtDesc(user.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void markAsRead(Long notificationId, Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        repo.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                n.setRead(true);
                repo.save(n);
            }
        });
    }

    @Transactional
    public void markAllAsRead(Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        repo.markAllReadByUserId(user.getId());
    }

    @Transactional
    public void deleteNotification(Long notificationId, Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        repo.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(user.getId())) {
                repo.delete(n);
            }
        });
    }

    @Transactional
    public void deleteAllNotifications(Authentication authentication) {
        requireAuthenticated(authentication);
        User user = userService.getUserByUsername(authentication.getName());
        repo.deleteAllByUserId(user.getId());
    }

    // ── DTO ────────────────────────────────────────────────────────────────

    public Map<String, Object> toDto(Notification n) {
        return Map.of(
                "id",          n.getId(),
                "type",        n.getType(),
                "message",     n.getMessage(),
                "targetUrl",   n.getTargetUrl() != null ? n.getTargetUrl() : "",
                "referenceId", n.getReferenceId() != null ? n.getReferenceId() : 0L,
                "read",        n.isRead(),
                "createdAt",   n.getCreatedAt().toString()
        );
    }

    private void requireAuthenticated(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new InsufficientAuthenticationException("Authentication required");
        }
    }
}
