package com.jiawa.lyw.itinerarymap.infrastructure.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.config.AmapWebServiceProperties;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.Coordinate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.AmapProviderException;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.ProviderFailure;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AmapRoutePlanningGatewayTests {
    private static final String KEY = "TEST-amap-web-service-key";
    private static final Instant NOW = Instant.parse("2026-09-05T08:00:00Z");
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void usesModeEndpointsAndSixDecimalGcj02Coordinates() throws Exception {
        List<URI> requests = new ArrayList<>();
        start(exchange -> {
            requests.add(exchange.getRequestURI());
            respond(exchange, 200, route("10", "20", "120.000000,30.000000;121.000000,31.000000"));
        });
        var gateway = gateway(new ArrayList<>());
        Coordinate origin = new Coordinate(new BigDecimal("120.1"), new BigDecimal("30.2"));
        Coordinate destination = new Coordinate(new BigDecimal("121.1234567"), new BigDecimal("31.7654321"));

        gateway.plan(origin, destination, RouteMode.WALKING, null);
        gateway.plan(origin, destination, RouteMode.DRIVING, null);
        gateway.plan(origin, destination, RouteMode.TRANSIT, "310100");

        assertEquals(List.of("/v5/direction/walking", "/v5/direction/driving", "/v5/direction/transit/integrated"),
                requests.stream().map(URI::getPath).toList());
        for (URI request : requests) {
            Map<String, String> query = query(request);
            assertEquals("120.100000,30.200000", query.get("origin"));
            assertEquals("121.123457,31.765432", query.get("destination"));
            assertEquals(KEY, query.get("key"));
        }
        assertEquals("310100", query(requests.get(2)).get("city1"));
        assertEquals("310100", query(requests.get(2)).get("city2"));
    }

    @Test
    void readsFirstRouteAndMergesStepPolylinesWithoutAdjacentDuplicates() throws Exception {
        start(exchange -> respond(exchange, 200, """
                {"status":"1","route":{"paths":[
                  {"distance":"1200","cost":{"duration":"600"},"steps":[
                    {"polyline":"116.100000,39.100000;116.200000,39.200000"},
                    {"polyline":"116.200000,39.200000;116.300000,39.300000"}
                  ]},
                  {"distance":"9999","cost":{"duration":"9999"},"steps":[]}
                ]}}
                """));

        var route = gateway(new ArrayList<>()).plan(coordinate("116.1", "39.1"), coordinate("116.3", "39.3"),
                RouteMode.DRIVING, null);

        assertEquals(1200, route.distanceMeters());
        assertEquals(600, route.durationSeconds());
        assertEquals(3, route.polyline().size());
        assertEquals(new BigDecimal("116.200000"), route.polyline().get(1).longitude());
        assertEquals(NOW, route.calculatedAt());
    }

    @Test
    void skipsAnIncompleteFirstRouteAndUsesTheFirstUsableRoute() throws Exception {
        start(exchange -> respond(exchange, 200, """
                {"status":"1","route":{"paths":[
                  {"distance":"not-a-number","cost":{"duration":"600"},"steps":[]},
                  {"distance":"42","cost":{"duration":"84"},"steps":[{"polyline":"100.0,30.0;101.0,31.0"}]}
                ]}}
                """));

        var route = gateway(new ArrayList<>()).plan(coordinate("100", "30"), coordinate("101", "31"),
                RouteMode.DRIVING, null);

        assertEquals(42, route.distanceMeters());
        assertEquals(84, route.durationSeconds());
    }

    @Test
    void thinsPolylineToTwoThousandPointsAndKeepsEndpoints() throws Exception {
        StringBuilder polyline = new StringBuilder();
        for (int index = 0; index < 2_101; index++) {
            if (index > 0) polyline.append(';');
            polyline.append("100.").append(String.format("%06d", index)).append(",30.000000");
        }
        start(exchange -> respond(exchange, 200, route("1", "2", polyline.toString())));

        var route = gateway(new ArrayList<>()).plan(coordinate("100", "30"), coordinate("101", "31"),
                RouteMode.WALKING, null);

        assertEquals(2_000, route.polyline().size());
        assertEquals(new BigDecimal("100.000000"), route.polyline().get(0).longitude());
        assertEquals(new BigDecimal("100.002100"), route.polyline().get(1_999).longitude());
    }

    @Test
    void rejectsTransitWithoutCityBeforeCallingProvider() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, route("1", "2", "100.0,30.0;101.0,31.0"));
        });

        AmapProviderException exception = assertThrows(AmapProviderException.class,
                () -> gateway(new ArrayList<>()).plan(coordinate("100", "30"), coordinate("101", "31"),
                        RouteMode.TRANSIT, null));

        assertEquals(ProviderFailure.INVALID_PROVIDER_RESPONSE, exception.failure());
        assertEquals(0, calls.get());
    }

    @Test
    void retriesTransientHttpFailureOnlyOnceAndDoesNotRetryEmptyRoute() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        start(exchange -> {
            int call = calls.incrementAndGet();
            respond(exchange, call == 1 ? 500 : 200,
                    call == 1 ? "private body" : route("10", "20", "100.0,30.0;101.0,31.0"));
        });
        gateway(delays).plan(coordinate("100", "30"), coordinate("101", "31"), RouteMode.DRIVING, null);
        assertEquals(2, calls.get());
        assertEquals(List.of(50L), delays);

        stopServer();
        server = null;
        calls.set(0);
        delays.clear();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "{\"status\":\"1\",\"route\":{\"paths\":[]}}");
        });
        AmapProviderException exception = assertThrows(AmapProviderException.class,
                () -> gateway(delays).plan(coordinate("100", "30"), coordinate("101", "31"),
                        RouteMode.DRIVING, null));
        assertEquals(ProviderFailure.INVALID_PROVIDER_RESPONSE, exception.failure());
        assertEquals(1, calls.get());
        assertEquals(List.of(), delays);
        assertEquals("INVALID_PROVIDER_RESPONSE", exception.getMessage());
    }

    private AmapRoutePlanningGateway gateway(List<Long> delays) {
        var properties = new AmapWebServiceProperties(baseUrl(), KEY, Duration.ofSeconds(1),
                Duration.ofSeconds(1), 4, 524_288);
        return new AmapRoutePlanningGateway(properties, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC),
                delays::add, () -> 0L);
    }

    private static Coordinate coordinate(String longitude, String latitude) {
        return new Coordinate(new BigDecimal(longitude), new BigDecimal(latitude));
    }

    private void start(ThrowingHandler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            try {
                handler.handle(exchange);
            } finally {
                exchange.close();
            }
        });
        server.start();
    }

    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    private static String route(String distance, String duration, String polyline) {
        return "{\"status\":\"1\",\"route\":{\"paths\":[{\"distance\":\"" + distance
                + "\",\"cost\":{\"duration\":\"" + duration + "\"},\"steps\":[{\"polyline\":\""
                + polyline + "\"}]}]}}";
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private static Map<String, String> query(URI uri) {
        Map<String, String> values = new LinkedHashMap<>();
        Arrays.stream(uri.getRawQuery().split("&")).forEach(pair -> {
            String[] parts = pair.split("=", 2);
            values.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(parts.length == 2 ? parts[1] : "", StandardCharsets.UTF_8));
        });
        return values;
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
