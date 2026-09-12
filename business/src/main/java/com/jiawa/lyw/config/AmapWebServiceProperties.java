package com.jiawa.lyw.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;

/** 高德 Web Service 的唯一后端配置事实源，供行程与旧社区地点适配器共用。 */
@ConfigurationProperties(prefix = "app.map.amap")
public record AmapWebServiceProperties(
        URI baseUrl,
        String apiKey,
        Duration connectTimeout,
        Duration responseTimeout,
        int routeConcurrency,
        int maxResponseBytes
) {
    private static final Duration MAX_TIMEOUT = Duration.ofSeconds(30);
    private static final int MIN_RESPONSE_BYTES = 1_024;
    private static final int MAX_RESPONSE_BYTES = 512 * 1_024;

    public AmapWebServiceProperties {
        if (baseUrl == null || baseUrl.getScheme() == null
                || !Set.of("http", "https").contains(baseUrl.getScheme().toLowerCase(Locale.ROOT))
                || baseUrl.getHost() == null || baseUrl.getUserInfo() != null
                || baseUrl.getRawQuery() != null || baseUrl.getRawFragment() != null) {
            throw new IllegalArgumentException(
                    "Amap base URL must be an absolute http(s) URL without credentials, query or fragment");
        }
        apiKey = secret(apiKey);
        if (!positiveAtMost(connectTimeout) || !positiveAtMost(responseTimeout)) {
            throw new IllegalArgumentException("Amap timeout is outside the safe range");
        }
        if (routeConcurrency < 1 || routeConcurrency > 4) {
            throw new IllegalArgumentException("Amap route concurrency is outside the safe range");
        }
        if (maxResponseBytes < MIN_RESPONSE_BYTES || maxResponseBytes > MAX_RESPONSE_BYTES) {
            throw new IllegalArgumentException("Amap response limit is outside the safe range");
        }
    }

    @Override
    public String toString() {
        return "AmapWebServiceProperties[baseUrl=" + baseUrl + ", apiKey=<redacted>, connectTimeout="
                + connectTimeout + ", responseTimeout=" + responseTimeout + ", routeConcurrency="
                + routeConcurrency + ", maxResponseBytes=" + maxResponseBytes + "]";
    }

    private static boolean positiveAtMost(Duration value) {
        return value != null && !value.isNegative() && !value.isZero() && value.compareTo(MAX_TIMEOUT) <= 0;
    }

    private static String secret(String value) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.length() < 16 || normalized.length() > 512
                || "change-me".equalsIgnoreCase(normalized)) {
            throw new IllegalArgumentException("Amap API key is missing or unsafe");
        }
        return normalized;
    }
}
