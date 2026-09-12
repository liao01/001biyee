package com.jiawa.lyw.itinerarymap.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 使用隔离 Redis 验证路线缓存不依赖本机服务或 Java 原生序列化。 */
@EnabledIfEnvironmentVariable(named = "LYW_INTEGRATION_CONTAINERS", matches = "true",
        disabledReason = "Requires explicit --containers mode")
class RedisRouteReferenceCacheIT {
    private static final String PASSWORD = "change-me";
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    @Test
    void storesJsonWithSevenDayTtlAndDiscardsOnlyMalformedValues() throws Exception {
        String batch = UUID.randomUUID().toString().replace("-", "");
        try (var redisContainer = redis(batch)) {
            redisContainer.start();
            LettuceConnectionFactory connectionFactory = connectionFactory(redisContainer);
            try {
                StringRedisTemplate template = new StringRedisTemplate(connectionFactory);
                template.afterPropertiesSet();
                RedisRouteReferenceCache cache = new RedisRouteReferenceCache(template);
                String validKey = "itinerary-map:route:v1:" + "a".repeat(64);
                var expected = new ItineraryMapModels.CachedRoute(
                        new ItineraryMapModels.RouteValue(120L, 90L,
                                List.of(coordinate("116.397026", "39.918058"), coordinate("116.407026", "39.928058")),
                                NOW),
                        NOW.plus(Duration.ofHours(24))
                );

                cache.put(validKey, expected, Duration.ofDays(7));
                RedisRouteReferenceCache reopened = new RedisRouteReferenceCache(template);

                assertEquals(expected, reopened.find(validKey).orElseThrow());
                Long ttlMillis = template.getExpire(validKey, TimeUnit.MILLISECONDS);
                assertTrue(ttlMillis > Duration.ofDays(6).plusHours(23).toMillis(), "TTL must remain near seven days");
                assertTrue(ttlMillis <= Duration.ofDays(7).toMillis(), "TTL must not exceed seven days");

                String malformedKey = "itinerary-map:route:v1:" + "b".repeat(64);
                template.opsForValue().set(malformedKey, "{not-json");

                assertTrue(cache.find(malformedKey).isEmpty());
                assertNull(template.opsForValue().get(malformedKey), "Only the malformed cache key is deleted");

                String semanticallyInvalidKey = "itinerary-map:route:v1:" + "c".repeat(64);
                cache.put(semanticallyInvalidKey, expected, Duration.ofDays(7));
                String serialized = template.opsForValue().get(semanticallyInvalidKey);
                ObjectNode corrupted = (ObjectNode) new ObjectMapper().readTree(serialized);
                corrupted.put("freshUntil", expected.freshUntil().plus(Duration.ofHours(1)).toString());
                template.opsForValue().set(semanticallyInvalidKey, new ObjectMapper().writeValueAsString(corrupted));

                assertTrue(cache.find(semanticallyInvalidKey).isEmpty());
                assertNull(template.opsForValue().get(semanticallyInvalidKey),
                        "A semantically invalid fresh-until value is also discarded");
                assertEquals(expected, cache.find(validKey).orElseThrow(), "The valid key remains readable");
            } finally {
                connectionFactory.destroy();
            }
        }
    }

    private GenericContainer<?> redis(String batch) {
        return new GenericContainer<>("redis:7.4.5-alpine").withExposedPorts(6379).withReuse(false)
                .withLabel("lyw.purpose", "itinerary-map-redis-test")
                .withLabel("lyw.test-batch", batch)
                .withCommand("redis-server", "--save", "", "--appendonly", "no", "--requirepass", PASSWORD)
                .withTmpFs(Map.of("/data", "rw,size=32m"))
                .waitingFor(Wait.forListeningPort().withStartupTimeout(Duration.ofSeconds(60)))
                .withCreateContainerCmdModifier(command -> {
                    command.withName("lyw-itinerary-map-redis-test-" + batch);
                    command.getHostConfig().withMemory(96L * 1024 * 1024).withNanoCPUs(1_000_000_000L);
                });
    }

    private LettuceConnectionFactory connectionFactory(GenericContainer<?> redisContainer) {
        RedisStandaloneConfiguration configuration = new RedisStandaloneConfiguration(
                redisContainer.getHost(), redisContainer.getMappedPort(6379)
        );
        configuration.setPassword(RedisPassword.of(PASSWORD));
        LettuceConnectionFactory connectionFactory = new LettuceConnectionFactory(configuration);
        connectionFactory.afterPropertiesSet();
        return connectionFactory;
    }

    private ItineraryMapModels.Coordinate coordinate(String longitude, String latitude) {
        return new ItineraryMapModels.Coordinate(new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
