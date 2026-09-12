package com.jiawa.lyw.itineraryplanning.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@ConfigurationProperties(prefix = "app.ai.itinerary.dify")
public record DifyItineraryPlanningProperties(
        URI baseUrl,
        String apiKey,
        String userHashKey,
        String contractVersion,
        Duration connectTimeout,
        Duration readTimeout,
        int maxResponseBytes,
        int maxOperations
) {
    private static final Duration MAX_CONNECT_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration MAX_READ_TIMEOUT = Duration.ofMinutes(2);
    private static final int MIN_RESPONSE_BYTES = 1_024;
    private static final int MAX_RESPONSE_BYTES = 2 * 1_024 * 1_024;
    private static final int MAX_OPERATIONS = 200;

    public DifyItineraryPlanningProperties {
        if (baseUrl == null || baseUrl.getScheme() == null
                || !Set.of("http", "https").contains(baseUrl.getScheme())
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getQuery() != null || baseUrl.getFragment() != null) {
            throw new IllegalArgumentException(
                    "Dify itinerary base URL must be an absolute http(s) URL without credentials, query or fragment");
        }
        apiKey = secret(apiKey, 16, "Dify itinerary API key");
        userHashKey = secret(userHashKey, 32, "Dify itinerary user hash key");
        contractVersion = text(contractVersion, 64, "Dify itinerary contract version");
        if (!positiveAtMost(connectTimeout, MAX_CONNECT_TIMEOUT)) {
            throw new IllegalArgumentException("Dify itinerary connect timeout is outside the safe range");
        }
        if (!positiveAtMost(readTimeout, MAX_READ_TIMEOUT)) {
            throw new IllegalArgumentException("Dify itinerary read timeout is outside the safe range");
        }
        if (maxResponseBytes < MIN_RESPONSE_BYTES || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Dify itinerary response limit is outside the safe range");
        }
        if (maxOperations < 1 || maxOperations > MAX_OPERATIONS) {
            throw new IllegalArgumentException("Dify itinerary operation limit is outside the safe range");
        }
    }

    @Override
    public String toString() {
        return "DifyItineraryPlanningProperties[baseUrl=" + baseUrl
                + ", apiKey=<redacted>, userHashKey=<redacted>, contractVersion=" + contractVersion
                + ", connectTimeout=" + connectTimeout + ", readTimeout=" + readTimeout
                + ", maxResponseBytes=" + maxResponseBytes + ", maxOperations=" + maxOperations + "]";
    }

    private static boolean positiveAtMost(Duration value, Duration maximum) {
        return value != null && !value.isZero() && !value.isNegative() && value.compareTo(maximum) <= 0;
    }

    private static String secret(String value, int minimumLength, String name) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.length() < minimumLength || normalized.length() > 512
                || "change-me".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException(name + " is missing or unsafe");
        }
        return normalized;
    }

    private static String text(String value, int maximumLength, String name) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maximumLength) {
            throw new IllegalArgumentException(name + " is missing or invalid");
        }
        return normalized;
    }
}
