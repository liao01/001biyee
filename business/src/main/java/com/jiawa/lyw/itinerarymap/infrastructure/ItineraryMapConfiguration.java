package com.jiawa.lyw.itinerarymap.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itinerary.application.ItineraryPlaceApplicationService;
import com.jiawa.lyw.itinerarymap.application.DefaultItineraryMapApplicationService;
import com.jiawa.lyw.itinerarymap.application.ItineraryMapApplicationService;
import com.jiawa.lyw.itinerarymap.application.PlaceSearchGateway;
import com.jiawa.lyw.itinerarymap.application.RoutePlanningGateway;
import com.jiawa.lyw.itinerarymap.application.RouteReferenceCache;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.AmapMapProperties;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.AmapPlaceSearchGateway;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.AmapRoutePlanningGateway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/** 地图模块的装配边界：供应商适配、派生缓存与最多四条并发路线。 */
@Configuration
@ConditionalOnProperty(prefix = "app.map.amap", name = "base-url")
@EnableConfigurationProperties(AmapMapProperties.class)
public class ItineraryMapConfiguration {
    @Bean(destroyMethod = "shutdown")
    ExecutorService itineraryMapRouteExecutor(AmapMapProperties properties) {
        AtomicInteger sequence = new AtomicInteger();
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "itinerary-map-route-" + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newFixedThreadPool(properties.routeConcurrency(), threadFactory);
    }

    @Bean
    PlaceSearchGateway amapPlaceSearchGateway(AmapMapProperties properties, ObjectMapper objectMapper) {
        return new AmapPlaceSearchGateway(properties, objectMapper);
    }

    @Bean
    RoutePlanningGateway amapRoutePlanningGateway(
            AmapMapProperties properties,
            ObjectMapper objectMapper,
            Clock clock
    ) {
        return new AmapRoutePlanningGateway(properties, objectMapper, clock);
    }

    @Bean
    RouteReferenceCache routeReferenceCache(StringRedisTemplate redis) {
        return new RedisRouteReferenceCache(redis);
    }

    @Bean
    ItineraryMapApplicationService itineraryMapApplicationService(
            ItineraryPlaceApplicationService itineraryPlaces,
            PlaceSearchGateway placeSearch,
            RoutePlanningGateway routes,
            RouteReferenceCache cache,
            @Qualifier("itineraryMapRouteExecutor") Executor routeExecutor,
            Clock clock
    ) {
        return new DefaultItineraryMapApplicationService(
                itineraryPlaces, placeSearch, routes, cache, routeExecutor, clock
        );
    }
}
