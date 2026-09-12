package com.jiawa.lyw.itinerarymap.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itinerarymap.application.RouteReferenceCache;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.CachedRoute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;

/** Redis 仅保存可再生的路线参考；读取或写入故障都降级为缓存未命中。 */
public class RedisRouteReferenceCache implements RouteReferenceCache {
    private static final Logger LOG = LoggerFactory.getLogger(RedisRouteReferenceCache.class);
    private static final Pattern ROUTE_CACHE_KEY = Pattern.compile("itinerary-map:route:v1:[0-9a-f]{64}");

    private final StringRedisTemplate redis;
    private final ObjectMapper json;

    public RedisRouteReferenceCache(StringRedisTemplate redis) {
        this(redis, new ObjectMapper().findAndRegisterModules());
    }

    RedisRouteReferenceCache(StringRedisTemplate redis, ObjectMapper json) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.json = Objects.requireNonNull(json, "json").findAndRegisterModules();
    }

    @Override
    public Optional<CachedRoute> find(String key) {
        if (!validKey(key)) {
            return Optional.empty();
        }
        String value;
        try {
            value = redis.opsForValue().get(key);
        } catch (RuntimeException exception) {
            LOG.warn("itinerary-map route cache read failed [REDIS_READ_FAILURE]");
            return Optional.empty();
        }
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(json.readValue(value, CachedRoute.class));
        } catch (JsonProcessingException | RuntimeException exception) {
            deleteMalformedValue(key);
            LOG.warn("itinerary-map route cache value discarded [REDIS_MALFORMED_VALUE]");
            return Optional.empty();
        }
    }

    @Override
    public void put(String key, CachedRoute route, Duration ttl) {
        if (!validKey(key) || route == null || ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        try {
            redis.opsForValue().set(key, json.writeValueAsString(route), ttl);
        } catch (JsonProcessingException | RuntimeException exception) {
            LOG.warn("itinerary-map route cache write failed [REDIS_WRITE_FAILURE]");
        }
    }

    private void deleteMalformedValue(String key) {
        try {
            redis.delete(key);
        } catch (RuntimeException exception) {
            LOG.warn("itinerary-map route cache cleanup failed [REDIS_DELETE_FAILURE]");
        }
    }

    private static boolean validKey(String key) {
        return key != null && ROUTE_CACHE_KEY.matcher(key).matches();
    }
}
