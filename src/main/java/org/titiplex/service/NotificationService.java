package org.titiplex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.titiplex.persistence.model.LanguageFollow;
import org.titiplex.persistence.model.Notification;
import org.titiplex.persistence.model.Scenario;
import org.titiplex.persistence.model.User;
import org.titiplex.persistence.repo.NotificationRepository;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationService {

    private static final String TYPE_NEW_SCENARIO = "NEW_SCENARIO_IN_FOLLOWED_LANGUAGE";

    // userId → list of active SSE emitters
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    private final NotificationRepository repo;
    private final UserService userService;
    private final LanguageFollowService languageFollowService;
    private final LanguageService languageService;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationRepository repo,
            UserService userService,
            LanguageFollowService languageFollowService,
            LanguageService languageService,
            ObjectMapper objectMapper
    ) {
        this.repo = repo;
        this.userService = userService;
        this.languageFollowService = languageFollowService;
        this.languageService = languageService;
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
