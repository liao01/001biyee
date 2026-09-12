package com.jiawa.lyw.itinerarymap.infrastructure.amap;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmapMapPropertiesTests {
    @Test
    void rejectsUnsafeProductionProperties() {
        assertThrows(IllegalArgumentException.class, () -> new AmapMapProperties(
                URI.create("https://restapi.amap.com?key=leak"), "change-me",
                Duration.ofSeconds(1), Duration.ofSeconds(3), 4, 512_000));
    }

    @Test
    void rejectsOutOfRangeTimeoutConcurrencyAndResponseLimit() {
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("ftp://restapi.amap.com"), Duration.ofSeconds(1), Duration.ofSeconds(3), 4, 512_000));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://user@restapi.amap.example.test"), Duration.ofSeconds(1), Duration.ofSeconds(3), 4, 512_000));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://restapi.amap.com"), Duration.ZERO, Duration.ofSeconds(3), 4, 512_000));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://restapi.amap.com"), Duration.ofSeconds(1), Duration.ofSeconds(31), 4, 512_000));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://restapi.amap.com"), Duration.ofSeconds(1), Duration.ofSeconds(3), 5, 512_000));
        assertThrows(IllegalArgumentException.class, () -> properties(
                URI.create("https://restapi.amap.com"), Duration.ofSeconds(1), Duration.ofSeconds(3), 4, 524_289));
    }

    @Test
    void redactsSecretFromToString() {
        AmapMapProperties value = validProperties("TEST-amap-web-service-key");

        assertFalse(value.toString().contains("TEST-amap-web-service-key"));
        assertTrue(value.toString().contains("<redacted>"));
    }

    private static AmapMapProperties validProperties(String key) {
        return new AmapMapProperties(URI.create("https://restapi.amap.com"), key,
                Duration.ofSeconds(1), Duration.ofSeconds(3), 4, 512_000);
    }

    private static AmapMapProperties properties(
            URI baseUrl, Duration connectTimeout, Duration responseTimeout, int concurrency, int maximum
    ) {
        return new AmapMapProperties(baseUrl, "TEST-amap-web-service-key", connectTimeout,
                responseTimeout, concurrency, maximum);
    }
}
