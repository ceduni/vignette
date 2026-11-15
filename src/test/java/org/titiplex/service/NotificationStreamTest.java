package org.titiplex.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class NotificationStreamTest {

    private final NotificationStream stream = new NotificationStream(new ObjectMapper());

    @Test
    void push_doesNotBuildThePayloadWhenTheUserIsNotConnected() {
        assertDoesNotThrow(() -> stream.push(1L, () -> {
            throw new AssertionError("the payload must not be built for a user with no open connection");
        }));
    }

    @Test
    void push_buildsThePayloadOnceForAConnectedUser_whateverTheNumberOfTabs() {
        assertNotNull(stream.open(1L));
        assertNotNull(stream.open(1L));
        AtomicInteger built = new AtomicInteger();

        stream.push(1L, () -> {
            built.incrementAndGet();
            return Map.of("id", 7);
        });

        assertEquals(1, built.get());
    }

    @Test
    void push_onlyReachesTheTargetedUser() {
        stream.open(1L);

        assertDoesNotThrow(() -> stream.push(2L, () -> {
            throw new AssertionError("user 2 is not connected");
        }));
    }
}
