package com.jiawa.lyw.itinerarymap.domain;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/** 地图模块的供应商无关值对象；不把高德 DTO 或行程内部模型扩散到模块边界。 */
public final class ItineraryMapModels {
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final int MAX_POLYLINE_COORDINATES = 20_000;
    private static final Duration ROUTE_FRESHNESS = Duration.ofHours(24);

    private ItineraryMapModels() {
    }

    public enum PlaceSource {
        AMAP,
        MANUAL
    }

    public enum RouteMode {
        WALKING,
        DRIVING,
        TRANSIT
    }

    public enum RouteStatus {
        LIVE,
        CACHED,
        UNAVAILABLE
    }

    public enum StopStatus {
        RESOLVED,
        UNRESOLVED
    }

    public record Coordinate(BigDecimal longitude, BigDecimal latitude) {
        public Coordinate {
            longitude = coordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE);
            latitude = coordinate(latitude, MIN_LATITUDE, MAX_LATITUDE);
        }
    }

    public record PlaceSearchQuery(String keyword, String city, int limit) {
        public PlaceSearchQuery {
            keyword = requiredText(keyword, 80, ItineraryMapError.INVALID_PLACE_QUERY, "地点搜索条件无效");
            city = optionalText(city, 80, ItineraryMapError.INVALID_PLACE_QUERY, "地点搜索条件无效");
            if (limit == 0) {
                limit = 10;
            }
            if (limit < 1 || limit > 20) {
                throw invalid(ItineraryMapError.INVALID_PLACE_QUERY, "地点搜索条件无效");
            }
        }
    }

    public record PlaceCandidate(
            PlaceSource source,
            String sourcePlaceId,
            String name,
            String address,
            String administrativeAreaCode,
            Coordinate coordinate,
            String coordinateSystem,
            String administrativeArea
    ) {
        public PlaceCandidate {
            source = requireSource(source);
            sourcePlaceId = normalizedSourcePlaceId(source, sourcePlaceId);
            name = requiredText(name, 200, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
            address = optionalText(address, 500, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
            administrativeAreaCode = normalizedAdministrativeAreaCode(administrativeAreaCode);
            coordinate = requiredValue(coordinate, "地点数据无效");
            coordinateSystem = normalizedCoordinateSystem(coordinateSystem);
            administrativeArea = optionalText(administrativeArea, 200, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        }
    }

    public record PlaceReference(
            PlaceSource source,
            String sourcePlaceId,
            String name,
            String address,
            String administrativeAreaCode,
            Coordinate coordinate,
            String coordinateSystem,
            Instant confirmedAt,
            Instant updatedAt
    ) {
        public PlaceReference {
            source = requireSource(source);
            sourcePlaceId = normalizedSourcePlaceId(source, sourcePlaceId);
            name = requiredText(name, 200, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
            address = optionalText(address, 500, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
            administrativeAreaCode = normalizedAdministrativeAreaCode(administrativeAreaCode);
            coordinate = requiredValue(coordinate, "地点数据无效");
            coordinateSystem = normalizedCoordinateSystem(coordinateSystem);
            confirmedAt = requiredValue(confirmedAt, "地点数据无效");
            updatedAt = requiredValue(updatedAt, "地点数据无效");
            if (updatedAt.isBefore(confirmedAt)) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
            }
        }
    }

    public record RouteValue(
            long distanceMeters,
            long durationSeconds,
            List<Coordinate> polyline,
            Instant calculatedAt
    ) {
        public RouteValue {
            if (distanceMeters < 0 || durationSeconds < 0) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "路线数据无效");
            }
            polyline = copyCoordinates(polyline, MAX_POLYLINE_COORDINATES);
            calculatedAt = requiredValue(calculatedAt, "路线数据无效");
        }
    }

    public record CachedRoute(RouteValue route, Instant freshUntil) {
        public CachedRoute {
            route = requiredValue(route, "缓存路线数据无效");
            freshUntil = requiredValue(freshUntil, "缓存路线数据无效");
            if (!freshUntil.equals(route.calculatedAt().plus(ROUTE_FRESHNESS))) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "缓存路线数据无效");
            }
        }
    }

    public record Stop(
            int sequence,
            long itemId,
            String title,
            LocalTime startTime,
            LocalTime endTime,
            String placeName,
            PlaceReference placeReference,
            StopStatus status
    ) {
        public Stop {
            if (sequence < 1 || itemId < 1) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地图停靠点无效");
            }
            title = requiredText(title, 120, ItineraryMapError.INVALID_MAP_REQUEST, "地图停靠点无效");
            placeName = optionalText(placeName, 200, ItineraryMapError.INVALID_MAP_REQUEST, "地图停靠点无效");
            status = requiredValue(status, "地图停靠点无效");
            if ((status == StopStatus.RESOLVED) != (placeReference != null)) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地图停靠点无效");
            }
            if (startTime != null && endTime != null && !endTime.isAfter(startTime)) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地图停靠点无效");
            }
        }
    }

    public record Leg(
            long fromItemId,
            long toItemId,
            RouteMode mode,
            RouteStatus status,
            Long distanceMeters,
            Long durationSeconds,
            List<Coordinate> polyline,
            Instant calculatedAt,
            boolean stale,
            String unavailableReason
    ) {
        public Leg {
            if (fromItemId < 1 || toItemId < 1 || fromItemId == toItemId) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "路线段无效");
            }
            mode = requiredValue(mode, "路线段无效");
            status = requiredValue(status, "路线段无效");
            polyline = copyCoordinates(polyline, 2_000);
            unavailableReason = optionalText(
                    unavailableReason, 80, ItineraryMapError.INVALID_MAP_REQUEST, "路线段无效"
            );
            boolean available = status == RouteStatus.LIVE || status == RouteStatus.CACHED;
            if (available) {
                if (distanceMeters == null || durationSeconds == null || distanceMeters < 0 || durationSeconds < 0
                        || calculatedAt == null || unavailableReason != null
                        || (status == RouteStatus.LIVE && stale)) {
                    throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "路线段无效");
                }
            } else if (distanceMeters != null || durationSeconds != null || !polyline.isEmpty()
                    || calculatedAt != null || stale || unavailableReason == null) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "路线段无效");
            }
        }
    }

    public record DailyMapView(
            long itineraryId,
            long dayId,
            LocalDate date,
            RouteMode mode,
            List<Stop> stops,
            List<Leg> legs,
            boolean degraded,
            Instant generatedAt
    ) {
        public DailyMapView {
            if (itineraryId < 1 || dayId < 1) {
                throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "每日地图无效");
            }
            date = requiredValue(date, "每日地图无效");
            mode = requiredValue(mode, "每日地图无效");
            stops = copyList(stops, "stops");
            legs = copyList(legs, "legs");
            generatedAt = requiredValue(generatedAt, "每日地图无效");
        }
    }

    private static PlaceSource requireSource(PlaceSource source) {
        if (source == null) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        }
        return source;
    }

    private static String normalizedSourcePlaceId(PlaceSource source, String value) {
        String normalized = optionalText(value, 128, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        if ((source == PlaceSource.AMAP && normalized == null)
                || (source == PlaceSource.MANUAL && normalized != null)) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        }
        return normalized;
    }

    private static String normalizedAdministrativeAreaCode(String value) {
        String normalized = optionalText(value, 6, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        if (normalized != null && !normalized.matches("[0-9]{6}")) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        }
        return normalized;
    }

    private static String normalizedCoordinateSystem(String value) {
        String normalized = requiredText(value, 16, ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        if (!"GCJ02".equals(normalized)) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "地点数据无效");
        }
        return normalized;
    }

    private static BigDecimal coordinate(BigDecimal value, BigDecimal min, BigDecimal max) {
        if (value == null || value.scale() > 7 || value.compareTo(min) < 0 || value.compareTo(max) > 0) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "坐标无效");
        }
        return value;
    }

    private static List<Coordinate> copyCoordinates(List<Coordinate> coordinates, int maximum) {
        if (coordinates == null || coordinates.size() > maximum || coordinates.stream().anyMatch(Objects::isNull)) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, "路线数据无效");
        }
        return List.copyOf(coordinates);
    }

    private static <T> List<T> copyList(List<T> values, String name) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, name + " 无效");
        }
        return List.copyOf(values);
    }

    private static <T> T requiredValue(T value, String message) {
        if (value == null) {
            throw invalid(ItineraryMapError.INVALID_MAP_REQUEST, message);
        }
        return value;
    }

    private static String requiredText(String value, int maxCodePoints, ItineraryMapError error, String message) {
        String normalized = value == null ? null : value.strip();
        if (normalized == null || normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw invalid(error, message);
        }
        return normalized;
    }

    private static String optionalText(String value, int maxCodePoints, ItineraryMapError error, String message) {
        if (value == null) {
            return null;
        }
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw invalid(error, message);
        }
        return normalized;
    }

    private static ItineraryMapException invalid(ItineraryMapError error, String message) {
        return new ItineraryMapException(error, message);
    }
}
