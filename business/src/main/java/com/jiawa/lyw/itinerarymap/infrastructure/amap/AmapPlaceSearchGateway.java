package com.jiawa.lyw.itinerarymap.infrastructure.amap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itinerarymap.application.PlaceSearchGateway;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.Coordinate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceCandidate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceSearchQuery;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceSource;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.AmapProviderException;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.ProviderFailure;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.LongConsumer;
import java.util.function.LongSupplier;

/** 将高德 POI 关键词结果转换为供应商无关地点候选。 */
public final class AmapPlaceSearchGateway implements PlaceSearchGateway {
    private final ObjectMapper objectMapper;
    private final AmapHttpExecutor http;

    public AmapPlaceSearchGateway(AmapMapProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, AmapPlaceSearchGateway::sleep, () -> (long) (Math.random() * 101));
    }

    AmapPlaceSearchGateway(
            AmapMapProperties properties,
            ObjectMapper objectMapper,
            LongConsumer sleeper,
            LongSupplier jitter
    ) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper").copy();
        this.http = new AmapHttpExecutor(properties, sleeper, jitter);
    }

    @Override
    public List<PlaceCandidate> search(PlaceSearchQuery query) {
        Objects.requireNonNull(query, "query");
        Map<String, String> parameters = new LinkedHashMap<>();
        parameters.put("keywords", query.keyword());
        if (query.city() != null) {
            parameters.put("city", query.city());
        }
        parameters.put("citylimit", "true");
        parameters.put("offset", Integer.toString(query.limit()));
        parameters.put("page", "1");
        parameters.put("extensions", "base");
        parameters.put("output", "JSON");

        JsonNode root = response(http.get("/v3/place/text", parameters));
        if (!"1".equals(text(root, "status"))) {
            throw failure(ProviderFailure.PROVIDER_UNAVAILABLE);
        }
        JsonNode pois = root.get("pois");
        if (pois == null || !pois.isArray()) {
            throw failure(ProviderFailure.INVALID_PROVIDER_RESPONSE);
        }
        List<PlaceCandidate> candidates = new ArrayList<>();
        for (JsonNode poi : pois) {
            PlaceCandidate candidate = candidate(poi);
            if (candidate != null) {
                candidates.add(candidate);
            }
        }
        return List.copyOf(candidates);
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

    private static PlaceCandidate candidate(JsonNode poi) {
        try {
            String id = text(poi, "id");
            String name = text(poi, "name");
            Coordinate coordinate = coordinate(text(poi, "location"));
            if (id == null || name == null || coordinate == null) {
                return null;
            }
            String adcode = text(poi, "adcode");
            if (adcode != null && !adcode.matches("[0-9]{6}")) {
                adcode = null;
            }
            return new PlaceCandidate(
                    PlaceSource.AMAP,
                    id,
                    name,
                    text(poi, "address"),
                    adcode,
                    coordinate,
                    "GCJ02",
                    administrativeArea(poi)
            );
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static Coordinate coordinate(String location) {
        if (location == null) {
            return null;
        }
        String[] values = location.split(",", -1);
        if (values.length != 2) {
            return null;
        }
        try {
            return new Coordinate(new BigDecimal(values[0].strip()), new BigDecimal(values[1].strip()));
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private static String administrativeArea(JsonNode poi) {
        StringBuilder result = new StringBuilder();
        for (String field : List.of("pname", "cityname", "adname")) {
            String value = text(poi, field);
            if (value != null) {
                result.append(value);
            }
        }
        return result.isEmpty() ? null : result.toString();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.isTextual()) {
            return null;
        }
        String normalized = value.textValue().strip();
        return normalized.isEmpty() ? null : normalized;
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
