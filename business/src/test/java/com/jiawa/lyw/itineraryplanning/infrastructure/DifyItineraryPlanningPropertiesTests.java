package com.jiawa.lyw.itineraryplanning.infrastructure;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DifyItineraryPlanningPropertiesTests {
    private static final String API_KEY = "TEST-dify-application-key";
    private static final String USER_HASH_KEY = "TEST-dify-user-hash-key-at-least-32-bytes";

    @Test
    void acceptsASecretBackedHttpEndpointAndBoundedRuntimeLimits() {
        DifyItineraryPlanningProperties properties = assertDoesNotThrow(() -> properties(
                URI.create("https://dify.example.test"),
                API_KEY,
                USER_HASH_KEY,
                Duration.ofSeconds(2),
                Duration.ofSeconds(45),
                262_144,
                80
        ));
        assertFalse(properties.toString().contains(API_KEY));
        assertFalse(properties.toString().contains(USER_HASH_KEY));
    }

    @Test
    void rejectsEndpointsThatCouldLeakCredentialsOrChangeTheRequestTarget() {
        for (String value : new String[]{
                "ftp://dify.example.test",
                "https://user:secret@dify.example.test",
                "https://dify.example.test?token=secret",
                "https://dify.example.test#fragment",
                "/relative"
        }) {
            assertThrows(IllegalArgumentException.class, () -> properties(
                    URI.create(value), API_KEY, USER_HASH_KEY,
                    Duration.ofSeconds(2), Duration.ofSeconds(45), 262_144, 80
            ), value);
        }
    }

    @Test
    void rejectsMissingSecretsAndUnsafeLimits() {
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://dify.example.test"), " ", USER_HASH_KEY,
                Duration.ofSeconds(2), Duration.ofSeconds(45), 262_144, 80
        ));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://dify.example.test"), API_KEY, "short",
                Duration.ofSeconds(2), Duration.ofSeconds(45), 262_144, 80
        ));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://dify.example.test"), API_KEY, USER_HASH_KEY,
                Duration.ZERO, Duration.ofSeconds(45), 262_144, 80
        ));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://dify.example.test"), API_KEY, USER_HASH_KEY,
                Duration.ofSeconds(2), Duration.ofMinutes(3), 262_144, 80
        ));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://dify.example.test"), API_KEY, USER_HASH_KEY,
                Duration.ofSeconds(2), Duration.ofSeconds(45), 512, 80
        ));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://dify.example.test"), API_KEY, USER_HASH_KEY,
                Duration.ofSeconds(2), Duration.ofSeconds(45), 262_144, 0
        ));
    }

    private DifyItineraryPlanningProperties properties(
            URI baseUrl,
            String apiKey,
            String userHashKey,
            Duration connectTimeout,
            Duration readTimeout,
            int maxResponseBytes,
            int maxOperations
    ) {
        return new DifyItineraryPlanningProperties(
                baseUrl,
                apiKey,
                userHashKey,
                "itinerary-revision/v1",
                connectTimeout,
                readTimeout,
                maxResponseBytes,
                maxOperations
        );
    }
}
