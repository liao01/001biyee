package com.jiawa.lyw.itinerarymap.application;

import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.application.ItineraryPlaceApplicationService;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapError;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapException;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.CachedRoute;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.Coordinate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.DailyMapView;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.Leg;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceCandidate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceReference;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceSearchQuery;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteStatus;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteValue;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.Stop;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.StopStatus;

import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * 先同步读取受访问控制的不可变日输入，再对独立相邻地点并发编排路线。
 * 任一缓存或供应商失败只影响该路线段，绝不改变正式行程事实。
 */
public class DefaultItineraryMapApplicationService implements ItineraryMapApplicationService {
    static final String CACHE_KEY_PREFIX = "itinerary-map:route:v1:";
    private static final Duration CACHE_TTL = Duration.ofDays(7);
    private static final Duration CACHE_FRESHNESS = Duration.ofHours(24);
    private static final Duration STALE_FALLBACK_WINDOW = Duration.ofDays(7);
    private static final int MAX_RESOLVED_STOPS = 50;
    private static final int MAX_RESPONSE_POLYLINE_POINTS = 2_000;

    private final ItineraryPlaceApplicationService itineraryPlaces;
    private final PlaceSearchGateway placeSearch;
    private final RoutePlanningGateway routes;
    private final RouteReferenceCache cache;
    private final Executor routeExecutor;
    private final Clock clock;
    private final Semaphore liveRoutePermits = new Semaphore(4, true);

    public DefaultItineraryMapApplicationService(
            ItineraryPlaceApplicationService itineraryPlaces,
            PlaceSearchGateway placeSearch,
            RoutePlanningGateway routes,
            RouteReferenceCache cache,
            Executor routeExecutor,
            Clock clock
    ) {
        this.itineraryPlaces = Objects.requireNonNull(itineraryPlaces, "itineraryPlaces");
        this.placeSearch = Objects.requireNonNull(placeSearch, "placeSearch");
        this.routes = Objects.requireNonNull(routes, "routes");
        this.cache = Objects.requireNonNull(cache, "cache");
        this.routeExecutor = Objects.requireNonNull(routeExecutor, "routeExecutor");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public List<PlaceCandidate> search(long actorMemberId, PlaceSearchQuery query) {
        requireActor(actorMemberId);
        Objects.requireNonNull(query, "query");
        try {
            List<PlaceCandidate> candidates = placeSearch.search(query);
            if (candidates == null || candidates.size() > query.limit()
                    || candidates.stream().anyMatch(Objects::isNull)) {
                throw new IllegalStateException("invalid map provider response");
            }
            return List.copyOf(candidates);
        } catch (RuntimeException exception) {
            throw new ItineraryMapException(
                    ItineraryMapError.MAP_PROVIDER_UNAVAILABLE, "地点搜索服务暂不可用"
            );
        }
    }

    @Override
    public ItineraryCommands.CommandResult confirmPlace(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.PutItemPlace> command
    ) {
        return itineraryPlaces.putPlace(actorMemberId, itineraryId, itemId, command);
    }

    @Override
    public ItineraryCommands.CommandResult removePlace(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.RemoveItemPlace> command
    ) {
        return itineraryPlaces.removePlace(actorMemberId, itineraryId, itemId, command);
    }

    @Override
    public DailyMapView dailyMap(long actorMemberId, long itineraryId, long dayId, RouteMode mode) {
        requireActor(actorMemberId);
        if (mode == null) {
            throw new ItineraryMapException(ItineraryMapError.INVALID_ROUTE_MODE, "路线方式无效");
        }

        // 必须发生在调用线程：下游任务只拿到脱敏、不可变的 DayMapInput。
        ItineraryModels.DayMapInput input = itineraryPlaces.dayMapInput(actorMemberId, itineraryId, dayId);
        if (input == null) {
            throw new ItineraryMapException(ItineraryMapError.INVALID_MAP_REQUEST, "每日地图请求无效");
        }
        Instant now = clock.instant();
        List<Stop> stops = new ArrayList<>();
        List<ResolvedStop> resolved = new ArrayList<>();
        int sequence = 1;
        for (ItineraryModels.MapItem item : input.items()) {
            PlaceReference reference = toMapReference(item.placeReference());
            StopStatus status = reference == null ? StopStatus.UNRESOLVED : StopStatus.RESOLVED;
            Stop stop = new Stop(sequence++, item.itemId(), item.title(), item.startTime(), item.endTime(),
                    item.placeName(), reference, status);
            stops.add(stop);
            if (reference != null) {
                resolved.add(new ResolvedStop(stop, reference));
            }
        }
        if (resolved.size() > MAX_RESOLVED_STOPS) {
            throw new ItineraryMapException(ItineraryMapError.INVALID_MAP_REQUEST, "每日地点数量超过上限");
        }

        List<CompletableFuture<IndexedLeg>> futures = new ArrayList<>();
        for (int index = 0; index + 1 < resolved.size(); index++) {
            ResolvedStop origin = resolved.get(index);
            ResolvedStop destination = resolved.get(index + 1);
            int routeIndex = index;
            try {
                futures.add(CompletableFuture.supplyAsync(
                        () -> new IndexedLeg(routeIndex, resolveLeg(origin, destination, mode, now)), routeExecutor
                ).exceptionally(exception -> new IndexedLeg(
                        routeIndex, unavailable(origin.stop().itemId(), destination.stop().itemId(), mode,
                        "ROUTE_PROVIDER_UNAVAILABLE")
                )));
            } catch (RuntimeException exception) {
                futures.add(CompletableFuture.completedFuture(new IndexedLeg(
                        routeIndex, unavailable(origin.stop().itemId(), destination.stop().itemId(), mode,
                        "ROUTE_PROVIDER_UNAVAILABLE")
                )));
            }
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        List<Leg> legs = futures.stream().map(CompletableFuture::join)
                .sorted(Comparator.comparingInt(IndexedLeg::index))
                .map(IndexedLeg::leg)
                .toList();
        boolean degraded = stops.stream().anyMatch(stop -> stop.status() == StopStatus.UNRESOLVED)
                || legs.stream().anyMatch(leg -> leg.stale() || leg.status() == RouteStatus.UNAVAILABLE);
        return new DailyMapView(input.itineraryId(), input.dayId(), input.date(), mode,
                stops, legs, degraded, now);
    }

    private Leg resolveLeg(ResolvedStop origin, ResolvedStop destination, RouteMode mode, Instant now) {
        String transitCityCode = transitCityCode(origin.reference(), destination.reference(), mode);
        if (mode == RouteMode.TRANSIT && transitCityCode == null) {
            return unavailable(origin.stop().itemId(), destination.stop().itemId(), mode, "TRANSIT_CITY_REQUIRED");
        }
        String key = routeCacheKey(origin.reference().coordinate(), destination.reference().coordinate(), mode,
                transitCityCode);
        Optional<CachedRoute> cached = safelyFind(key);
        if (cached.isPresent() && !cached.get().freshUntil().isBefore(now)) {
            return available(origin.stop().itemId(), destination.stop().itemId(), mode, RouteStatus.CACHED,
                    cached.get().route(), false);
        }

        try {
            RouteValue live = planWithBoundedConcurrency(origin.reference().coordinate(), destination.reference().coordinate(),
                    mode, transitCityCode);
            safelyPut(key, new CachedRoute(live, live.calculatedAt().plus(CACHE_FRESHNESS)));
            return available(origin.stop().itemId(), destination.stop().itemId(), mode, RouteStatus.LIVE, live, false);
        } catch (RuntimeException exception) {
            if (cached.isPresent() && !cached.get().route().calculatedAt().plus(STALE_FALLBACK_WINDOW).isBefore(now)) {
                return available(origin.stop().itemId(), destination.stop().itemId(), mode, RouteStatus.CACHED,
                        cached.get().route(), true);
            }
            return unavailable(origin.stop().itemId(), destination.stop().itemId(), mode, "ROUTE_PROVIDER_UNAVAILABLE");
        }
    }

    private RouteValue planWithBoundedConcurrency(
            Coordinate origin,
            Coordinate destination,
            RouteMode mode,
            String transitCityCode
    ) {
        boolean acquired = false;
        try {
            liveRoutePermits.acquire();
            acquired = true;
            RouteValue route = routes.plan(origin, destination, mode, transitCityCode);
            if (route == null) {
                throw new IllegalStateException("empty route provider response");
            }
            return route;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("route planning interrupted");
        } finally {
            if (acquired) {
                liveRoutePermits.release();
            }
        }
    }

    private Optional<CachedRoute> safelyFind(String key) {
        try {
            Optional<CachedRoute> found = cache.find(key);
            return found == null ? Optional.empty() : found;
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
    }

    private void safelyPut(String key, CachedRoute route) {
        try {
            cache.put(key, route, CACHE_TTL);
        } catch (RuntimeException ignored) {
            // 缓存不是正式事实，写失败不能把实时路线变为失败。
        }
    }

    static String routeCacheKey(Coordinate origin, Coordinate destination, RouteMode mode, String transitCityCode) {
        String canonical = "v1|AMAP|" + mode.name() + "|"
                + canonicalCoordinate(origin) + "|" + canonicalCoordinate(destination) + "|"
                + (transitCityCode == null ? "" : transitCityCode);
        try {
            return CACHE_KEY_PREFIX + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static String canonicalCoordinate(Coordinate coordinate) {
        return coordinate.longitude().setScale(6, RoundingMode.HALF_UP).toPlainString() + ','
                + coordinate.latitude().setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private static String transitCityCode(PlaceReference origin, PlaceReference destination, RouteMode mode) {
        if (mode != RouteMode.TRANSIT) {
            return null;
        }
        String originCity = cityContext(origin.administrativeAreaCode());
        String destinationCity = cityContext(destination.administrativeAreaCode());
        return originCity != null && originCity.equals(destinationCity) ? originCity : null;
    }

    private static String cityContext(String administrativeAreaCode) {
        if (administrativeAreaCode == null || !administrativeAreaCode.matches("[0-9]{6}")) {
            return null;
        }
        return administrativeAreaCode.substring(0, 4) + "00";
    }

    private static Leg available(
            long fromItemId,
            long toItemId,
            RouteMode mode,
            RouteStatus status,
            RouteValue route,
            boolean stale
    ) {
        return new Leg(fromItemId, toItemId, mode, status, route.distanceMeters(), route.durationSeconds(),
                thinPolyline(route.polyline()), route.calculatedAt(), stale, null);
    }

    private static Leg unavailable(long fromItemId, long toItemId, RouteMode mode, String reason) {
        return new Leg(fromItemId, toItemId, mode, RouteStatus.UNAVAILABLE, null, null,
                List.of(), null, false, reason);
    }

    private static List<Coordinate> thinPolyline(List<Coordinate> polyline) {
        if (polyline.size() <= MAX_RESPONSE_POLYLINE_POINTS) {
            return polyline;
        }
        List<Coordinate> result = new ArrayList<>(MAX_RESPONSE_POLYLINE_POINTS);
        int finalIndex = polyline.size() - 1;
        for (int outputIndex = 0; outputIndex < MAX_RESPONSE_POLYLINE_POINTS; outputIndex++) {
            int sourceIndex = (int) ((long) outputIndex * finalIndex / (MAX_RESPONSE_POLYLINE_POINTS - 1));
            result.add(polyline.get(sourceIndex));
        }
        return List.copyOf(result);
    }

    private static PlaceReference toMapReference(ItineraryModels.PlaceReference reference) {
        if (reference == null) {
            return null;
        }
        return new PlaceReference(
                ItineraryMapModels.PlaceSource.valueOf(reference.source().name()), reference.sourcePlaceId(), reference.name(),
                reference.address(), reference.administrativeAreaCode(),
                new Coordinate(reference.longitude(), reference.latitude()), reference.coordinateSystem(),
                reference.confirmedAt(), reference.updatedAt()
        );
    }

    private static void requireActor(long actorMemberId) {
        if (actorMemberId < 1) {
            throw new ItineraryMapException(ItineraryMapError.INVALID_MAP_REQUEST, "地图请求无效");
        }
    }

    private record ResolvedStop(Stop stop, PlaceReference reference) {
    }

    private record IndexedLeg(int index, Leg leg) {
    }
}
