package com.jiawa.lyw.itinerarymap.application;

import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.application.ItineraryPlaceApplicationService;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapError;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapException;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static com.jiawa.lyw.itinerary.domain.ItineraryModels.PlaceSource.AMAP;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode.DRIVING;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode.TRANSIT;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode.WALKING;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteStatus.CACHED;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteStatus.LIVE;
import static com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteStatus.UNAVAILABLE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItineraryMapApplicationServiceTests {
    private static final Instant NOW = Instant.parse("2026-09-05T00:00:00Z");

    @Mock private ItineraryPlaceApplicationService itineraryPlaces;
    @Mock private PlaceSearchGateway placeSearch;
    @Mock private RoutePlanningGateway routes;
    @Mock private RouteReferenceCache cache;

    private ItineraryMapApplicationService service;

    @BeforeEach
    void setUp() {
        service = new DefaultItineraryMapApplicationService(
                itineraryPlaces, placeSearch, routes, cache, Runnable::run,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        lenient().when(cache.find(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void buildsLegsAcrossUnresolvedItemsWithoutLosingStops() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(input(
                item(101L, reference("110101", "116.397026", "39.918058")),
                item(102L, null),
                item(103L, reference("110101", "116.407026", "39.928058"))
        ));
        when(routes.plan(any(), any(), eq(WALKING), eq(null))).thenReturn(route(NOW));

        var view = service.dailyMap(7L, 11L, 21L, WALKING);

        assertEquals(List.of(101L, 102L, 103L),
                view.stops().stream().map(ItineraryMapModels.Stop::itemId).toList());
        assertEquals(1, view.legs().size());
        assertEquals(101L, view.legs().get(0).fromItemId());
        assertEquals(103L, view.legs().get(0).toItemId());
        assertEquals(LIVE, view.legs().get(0).status());
        assertTrue(view.degraded());
    }

    @Test
    void usesFreshCacheWithoutCallingRouteGateway() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "110101"));
        var freshRoute = route(NOW.minus(Duration.ofHours(1)));
        when(cache.find(anyString())).thenReturn(Optional.of(new ItineraryMapModels.CachedRoute(
                freshRoute, freshRoute.calculatedAt().plus(Duration.ofHours(24))
        )));

        var view = service.dailyMap(7L, 11L, 21L, DRIVING);

        assertEquals(CACHED, view.legs().get(0).status());
        assertFalse(view.legs().get(0).stale());
        assertFalse(view.degraded());
        verify(routes, never()).plan(any(), any(), any(), any());
    }

    @Test
    void refreshesStaleCacheWhenLiveRouteSucceeds() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "110101"));
        var staleRoute = route(NOW.minus(Duration.ofDays(2)));
        when(cache.find(anyString())).thenReturn(Optional.of(new ItineraryMapModels.CachedRoute(
                staleRoute, staleRoute.calculatedAt().plus(Duration.ofHours(24))
        )));
        when(routes.plan(any(), any(), eq(DRIVING), eq(null))).thenReturn(route(NOW));

        var view = service.dailyMap(7L, 11L, 21L, DRIVING);

        assertEquals(LIVE, view.legs().get(0).status());
        verify(cache).put(anyString(), any(ItineraryMapModels.CachedRoute.class), eq(Duration.ofDays(7)));
    }

    @Test
    void servesStaleCacheWhenRouteProviderFails() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "110101"));
        var staleRoute = route(NOW.minus(Duration.ofDays(2)));
        when(cache.find(anyString())).thenReturn(Optional.of(new ItineraryMapModels.CachedRoute(
                staleRoute, staleRoute.calculatedAt().plus(Duration.ofHours(24))
        )));
        when(routes.plan(any(), any(), eq(DRIVING), eq(null))).thenThrow(new IllegalStateException("provider timeout"));

        var view = service.dailyMap(7L, 11L, 21L, DRIVING);

        assertEquals(CACHED, view.legs().get(0).status());
        assertTrue(view.legs().get(0).stale());
        assertTrue(view.degraded());
    }

    @Test
    void returnsUnavailableLegWhenNoCacheAndRouteProviderFails() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "110101"));
        when(routes.plan(any(), any(), eq(DRIVING), eq(null))).thenThrow(new IllegalStateException("provider timeout"));

        var view = service.dailyMap(7L, 11L, 21L, DRIVING);

        assertEquals(UNAVAILABLE, view.legs().get(0).status());
        assertEquals("ROUTE_PROVIDER_UNAVAILABLE", view.legs().get(0).unavailableReason());
        assertTrue(view.degraded());
    }

    @Test
    void doesNotUseCacheOlderThanTheSevenDayFallbackWindow() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "110101"));
        var expiredRoute = route(NOW.minus(Duration.ofDays(8)));
        when(cache.find(anyString())).thenReturn(Optional.of(new ItineraryMapModels.CachedRoute(
                expiredRoute, expiredRoute.calculatedAt().plus(Duration.ofHours(24))
        )));
        when(routes.plan(any(), any(), eq(DRIVING), eq(null))).thenThrow(new IllegalStateException("provider timeout"));

        var view = service.dailyMap(7L, 11L, 21L, DRIVING);

        assertEquals(UNAVAILABLE, view.legs().get(0).status());
        assertFalse(view.legs().get(0).stale());
    }

    @Test
    void treatsCacheReadAndWriteFailuresAsNonBlocking() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "110101"));
        when(cache.find(anyString())).thenThrow(new IllegalStateException("redis down"));
        when(routes.plan(any(), any(), eq(DRIVING), eq(null))).thenReturn(route(NOW));
        doThrow(new IllegalStateException("redis down")).when(cache)
                .put(anyString(), any(ItineraryMapModels.CachedRoute.class), any(Duration.class));

        var view = service.dailyMap(7L, 11L, 21L, DRIVING);

        assertEquals(LIVE, view.legs().get(0).status());
        assertFalse(view.degraded());
    }

    @Test
    void marksTransitWithoutSharedCityAsUnavailableWithoutFallback() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "310101"));

        var view = service.dailyMap(7L, 11L, 21L, TRANSIT);

        assertEquals(UNAVAILABLE, view.legs().get(0).status());
        assertEquals("TRANSIT_CITY_REQUIRED", view.legs().get(0).unavailableReason());
        verify(routes, never()).plan(any(), any(), any(), any());
    }

    @Test
    void enforcesResolvedStopLimitBeforeSchedulingRoutes() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(input(resolvedItems(50).toArray(ItineraryModels.MapItem[]::new)));
        when(routes.plan(any(), any(), any(), any())).thenReturn(route(NOW));

        var fifty = service.dailyMap(7L, 11L, 21L, WALKING);

        assertEquals(49, fifty.legs().size());
        verify(routes, org.mockito.Mockito.times(49)).plan(any(), any(), eq(WALKING), eq(null));

        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(input(resolvedItems(51).toArray(ItineraryModels.MapItem[]::new)));
        var exception = assertThrows(ItineraryMapException.class,
                () -> service.dailyMap(7L, 11L, 21L, WALKING));
        assertEquals(ItineraryMapError.INVALID_MAP_REQUEST, exception.error());
    }

    @Test
    void thinsLargePolylinesWhilePreservingEndpoints() {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(twoResolvedItems("110101", "110101"));
        var polyline = new ArrayList<ItineraryMapModels.Coordinate>();
        for (int index = 0; index < 2_100; index++) {
            polyline.add(coordinate("116." + String.format("%06d", index), "39.9"));
        }
        when(routes.plan(any(), any(), eq(WALKING), eq(null))).thenReturn(
                new ItineraryMapModels.RouteValue(120L, 90L, polyline, NOW)
        );

        var leg = service.dailyMap(7L, 11L, 21L, WALKING).legs().get(0);

        assertEquals(2_000, leg.polyline().size());
        assertEquals(polyline.get(0), leg.polyline().get(0));
        assertEquals(polyline.get(polyline.size() - 1), leg.polyline().get(leg.polyline().size() - 1));
    }

    @Test
    void boundsLiveProviderCallsToFourEvenWhenExecutorHasMoreThreads() throws Exception {
        when(itineraryPlaces.dayMapInput(7L, 11L, 21L)).thenReturn(
                input(resolvedItems(6).toArray(ItineraryModels.MapItem[]::new))
        );
        CountDownLatch firstFourStarted = new CountDownLatch(4);
        CountDownLatch releaseProvider = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        AtomicInteger maximumActive = new AtomicInteger();
        RoutePlanningGateway boundedGateway = (origin, destination, mode, city) -> {
            int current = active.incrementAndGet();
            maximumActive.accumulateAndGet(current, Math::max);
            firstFourStarted.countDown();
            try {
                if (!releaseProvider.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("test provider timed out");
                }
                return route(NOW);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("test provider interrupted", exception);
            } finally {
                active.decrementAndGet();
            }
        };
        ExecutorService routeExecutor = Executors.newFixedThreadPool(8);
        service = new DefaultItineraryMapApplicationService(
                itineraryPlaces, placeSearch, boundedGateway, cache, routeExecutor,
                Clock.fixed(NOW, ZoneOffset.UTC)
        );
        try {
            CompletableFuture<ItineraryMapModels.DailyMapView> result = CompletableFuture.supplyAsync(
                    () -> service.dailyMap(7L, 11L, 21L, WALKING)
            );

            assertTrue(firstFourStarted.await(3, TimeUnit.SECONDS));
            assertEquals(4, maximumActive.get());
            releaseProvider.countDown();
            assertEquals(5, result.get(10, TimeUnit.SECONDS).legs().size());
            assertTrue(maximumActive.get() <= 4);
        } finally {
            releaseProvider.countDown();
            routeExecutor.shutdownNow();
            assertTrue(routeExecutor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void derivesOpaqueCanonicalCacheKeysFromRoundedCoordinatesOnly() {
        String first = DefaultItineraryMapApplicationService.routeCacheKey(
                coordinate("116.3970264", "39.9180584"), coordinate("116.4070264", "39.9280584"), WALKING, null
        );
        String sameRounded = DefaultItineraryMapApplicationService.routeCacheKey(
                coordinate("116.3970263", "39.9180583"), coordinate("116.4070263", "39.9280583"), WALKING, null
        );
        String transit = DefaultItineraryMapApplicationService.routeCacheKey(
                coordinate("116.3970264", "39.9180584"), coordinate("116.4070264", "39.9280584"), TRANSIT, "110100"
        );

        assertEquals(first, sameRounded);
        assertTrue(first.matches("itinerary-map:route:v1:[0-9a-f]{64}"));
        assertFalse(first.equals(transit));
    }

    @Test
    void delegatesPlaceCommandsAndWrapsOnlySearchProviderFailures() {
        var command = new ItineraryCommands.CommandEnvelope<>(UUID.randomUUID(), 2L,
                new ItineraryCommands.PutItemPlace(AMAP, "B000A", "故宫", null,
                        "110101", new BigDecimal("116.397026"), new BigDecimal("39.918058"), "GCJ02"));
        var result = new ItineraryCommands.CommandResult(11L, 101L, 3L, false);
        when(itineraryPlaces.putPlace(7L, 11L, 101L, command)).thenReturn(result);

        assertEquals(result, service.confirmPlace(7L, 11L, 101L, command));
        when(placeSearch.search(any())).thenThrow(new IllegalStateException("provider down"));
        var exception = assertThrows(ItineraryMapException.class,
                () -> service.search(7L, new ItineraryMapModels.PlaceSearchQuery("故宫", null, 10)));
        assertEquals(ItineraryMapError.MAP_PROVIDER_UNAVAILABLE, exception.error());
    }

    private ItineraryModels.DayMapInput twoResolvedItems(String firstAreaCode, String secondAreaCode) {
        return input(
                item(101L, reference(firstAreaCode, "116.397026", "39.918058")),
                item(102L, reference(secondAreaCode, "116.407026", "39.928058"))
        );
    }

    private List<ItineraryModels.MapItem> resolvedItems(int count) {
        var items = new ArrayList<ItineraryModels.MapItem>();
        for (int index = 0; index < count; index++) {
            items.add(item(100L + index, reference("110101", "116.397026", "39.918058")));
        }
        return items;
    }

    private ItineraryModels.DayMapInput input(ItineraryModels.MapItem... items) {
        return new ItineraryModels.DayMapInput(11L, 21L, LocalDate.of(2026, 9, 5), List.of(items));
    }

    private ItineraryModels.MapItem item(long itemId, ItineraryModels.PlaceReference reference) {
        return new ItineraryModels.MapItem(itemId, "安排 " + itemId, LocalTime.of(9, 0),
                LocalTime.of(10, 0), "人工地点 " + itemId, reference, itemId);
    }

    private ItineraryModels.PlaceReference reference(String areaCode, String longitude, String latitude) {
        return new ItineraryModels.PlaceReference(AMAP, "poi-" + longitude, "地点", null, areaCode,
                new BigDecimal(longitude), new BigDecimal(latitude), "GCJ02", NOW, NOW);
    }

    private ItineraryMapModels.RouteValue route(Instant calculatedAt) {
        return new ItineraryMapModels.RouteValue(120L, 90L,
                List.of(coordinate("116.397026", "39.918058"), coordinate("116.407026", "39.928058")),
                calculatedAt);
    }

    private ItineraryMapModels.Coordinate coordinate(String longitude, String latitude) {
        return new ItineraryMapModels.Coordinate(new BigDecimal(longitude), new BigDecimal(latitude));
    }
}
