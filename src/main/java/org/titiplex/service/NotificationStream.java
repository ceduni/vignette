package org.titiplex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

/**
 * Live delivery of notifications to the browsers currently connected, over Server-Sent
 * Events. It only knows about open connections: what to notify, and to whom, is decided
 * by {@link NotificationService}.
 */
@Component
public class NotificationStream {

    private static final String EVENT_NAME = "notification";

    /** A user may have several tabs open: one emitter per connection. */
    private final Map<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;

    public NotificationStream(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** Opens a connection for this user; it stays registered until it completes, times out or fails. */
    public SseEmitter open(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emittersByUserId.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> close(userId, emitter));
        emitter.onTimeout(() -> close(userId, emitter));
        emitter.onError(e -> close(userId, emitter));

        // Send a keep-alive comment on connect
        try {
            emitter.send(SseEmitter.event().comment("connected"));
        } catch (IOException e) {
            close(userId, emitter);
        }
        return emitter;
    }

    /**
     * Sends the payload, as JSON, to every open connection of this user. The payload is only
     * built when the user is actually connected.
     */
    public void push(Long userId, Supplier<?> payload) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters == null || emitters.isEmpty()) return;

        String json;
        try {
            json = objectMapper.writeValueAsString(payload.get());
        } catch (Exception e) {
            return;
        }

        List<SseEmitter> dead = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name(EVENT_NAME).data(json));
            } catch (IOException e) {
                dead.add(emitter);
            }
        }
        dead.forEach(emitter -> close(userId, emitter));
    }

    private void close(Long userId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByUserId.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);
            if (emitters.isEmpty()) emittersByUserId.remove(userId);
        }
    }
}
