package com.jiawa.lyw.itinerarymap.infrastructure.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.config.AmapWebServiceProperties;
import com.jiawa.lyw.itinerarymap.application.RoutePlanningGateway;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.Coordinate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteValue;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.AmapProviderException;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.ProviderFailure;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** 将高德路线规划 2.0 的首条可用路线转换为统一路线值。 */
public final class AmapRoutePlanningGateway implements RoutePlanningGateway {
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final AmapHttpExecutor http;

    public AmapRoutePlanningGateway(AmapWebServiceProperties properties, ObjectMapper objectMapper, Clock clock) {
        this(properties, objectMapper, clock, AmapRoutePlanningGateway::sleep, () -> (long) (Math.random() * 101));
    }

    AmapRoutePlanningGateway(
            AmapWebServiceProperties properties,
            ObjectMapper objectMapper,
            Clock clock,
            LongConsumer sleeper,
            LongSupplier jitter
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
        this.clock = Objects.requireNonNull(clock, "clock");
        this.http = new AmapHttpExecutor(properties, sleeper, jitter);
    }

    @Override
    public RouteValue plan(Coordinate origin, Coordinate destination, RouteMode mode, String transitCityCode) {
        Objects.requireNonNull(origin, "origin");
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(mode, "mode");
        String path = path(mode);
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("origin", coordinate(origin));
        parameters.put("destination", coordinate(destination));
        if (mode == RouteMode.TRANSIT) {
            String city = transitCityCode == null ? null : transitCityCode.strip();
            if (city == null || !city.matches("[0-9]{6}")) {
                throw failure(ProviderFailure.INVALID_PROVIDER_RESPONSE);
            }
            parameters.put("city1", city);
            parameters.put("city2", city);
        }
        JsonNode root = response(http.get(path, parameters));
        if (!"1".equals(text(root, "status"))) {
            throw failure(ProviderFailure.PROVIDER_UNAVAILABLE);
        }
        for (JsonNode selected : routeCandidates(root.path("route"))) {
            long distance = nonNegativeNumber(selected, "distance");
            long duration = duration(selected);
            if (distance >= 0 && duration >= 0) {
                return new RouteValue(distance, duration, thin(collectPolyline(selected)), clock.instant());
            }
        }
        throw failure(ProviderFailure.INVALID_PROVIDER_RESPONSE);
    }

    private JsonNode response(AmapHttpExecutor.Response response) {
        try {
            JsonNode root = objectMapper.readTree(response.body());
            if (root == null || !root.isObject()) {
                throw failure(ProviderFailure.INVALID_PROVIDER_RESPONSE);
            }
            return root;
        } catch (AmapProviderException exception) {
            throw exception;
        } catch (Exception exception) {
            throw failure(ProviderFailure.INVALID_PROVIDER_RESPONSE);
        }
    }

    private static String path(RouteMode mode) {
        return switch (mode) {
            case WALKING -> "/v5/direction/walking";
            case DRIVING -> "/v5/direction/driving";
            case TRANSIT -> "/v5/direction/transit/integrated";
        };
    }

    private static String coordinate(Coordinate coordinate) {
        return coordinate.longitude().setScale(6, RoundingMode.HALF_UP).toPlainString()
                + "," + coordinate.latitude().setScale(6, RoundingMode.HALF_UP).toPlainString();
    }

    private static List<JsonNode> routeCandidates(JsonNode route) {
        if (route == null || !route.isObject()) {
            return List.of();
        }
        for (String field : List.of("paths", "transits")) {
            JsonNode values = route.get(field);
            if (values != null && values.isArray()) {
                List<JsonNode> candidates = new ArrayList<>();
                values.forEach(value -> {
                    if (value.isObject()) {
                        candidates.add(value);
                    }
                });
                if (!candidates.isEmpty()) {
                    return List.copyOf(candidates);
                }
            }
        }
        return List.of();
    }

    private static long duration(JsonNode route) {
        long direct = nonNegativeNumber(route, "duration");
        if (direct >= 0) {
            return direct;
        }
        return nonNegativeNumber(route.path("cost"), "duration");
    }

    private static long nonNegativeNumber(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !(value.isTextual() || value.isIntegralNumber())) {
            return -1;
        }
        try {
            long parsed = Long.parseLong(value.asText());
            return parsed < 0 ? -1 : parsed;
        } catch (NumberFormatException exception) {
            return -1;
        }
    }

    private static List<Coordinate> collectPolyline(JsonNode node) {
        List<Coordinate> result = new ArrayList<>();
        collectPolyline(node, result);
        return result;
    }

    private static void collectPolyline(JsonNode node, List<Coordinate> result) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            JsonNode polyline = node.get("polyline");
            if (polyline != null && polyline.isTextual()) {
                appendPolyline(polyline.textValue(), result);
            }
            node.fields().forEachRemaining(entry -> {
                if (!"polyline".equals(entry.getKey())) {
                    collectPolyline(entry.getValue(), result);
                }
            });
        } else if (node.isArray()) {
            node.forEach(value -> collectPolyline(value, result));
        }
    }

    private static void appendPolyline(String raw, List<Coordinate> result) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        for (String point : raw.split(";")) {
            String[] values = point.split(",", -1);
            if (values.length != 2) {
                continue;
            }
            try {
                Coordinate coordinate = new Coordinate(new BigDecimal(values[0].strip()), new BigDecimal(values[1].strip()));
                if (result.isEmpty() || !result.get(result.size() - 1).equals(coordinate)) {
                    result.add(coordinate);
                }
            } catch (RuntimeException ignored) {
                // 单个畸形折线点不污染已经可用的供应商路线。
            }
        }
    }

    private static List<Coordinate> thin(List<Coordinate> coordinates) {
        if (coordinates.size() <= 2_000) {
            return List.copyOf(coordinates);
        }
        List<Coordinate> result = new ArrayList<>(2_000);
        int lastIndex = coordinates.size() - 1;
        for (int index = 0; index < 2_000; index++) {
            int sourceIndex = (int) Math.floor((double) index * lastIndex / 1_999);
            result.add(coordinates.get(sourceIndex));
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank() ? value.textValue() : null;
    }

    private static AmapProviderException failure(ProviderFailure failure) {
        return new AmapProviderException(failure);
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Amap retry interrupted");
        }
    }
}
