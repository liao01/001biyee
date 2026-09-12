package com.jiawa.lyw.itinerarymap.infrastructure.amap;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.config.AmapWebServiceProperties;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceSearchQuery;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.AmapProviderException;
import com.jiawa.lyw.itinerarymap.infrastructure.amap.dto.AmapResponseModels.ProviderFailure;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.ServerSocket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AmapPlaceSearchGatewayTests {
    private static final String KEY = "TEST-amap-web-service-key";
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) server.stop(0);
    }

    @Test
    void encodesSearchRequestAndMapsOnlyValidPois() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<URI> requests = new ArrayList<>();
        start(exchange -> {
            calls.incrementAndGet();
            requests.add(exchange.getRequestURI());
            respond(exchange, 200, """
                    {"status":"1","pois":[
                      {"id":"B001","name":"湖畔 博物馆","address":"和平路 1 号","adcode":"310101","location":"121.500000,31.200000","pname":"上海市","cityname":"上海市","adname":"黄浦区"},
                      {"id":"","name":"invalid","location":"121.5,31.2"},
                      {"id":"B003","name":"invalid","location":"not-a-coordinate"}
                    ]}
                    """);
        });

        var result = gateway(Duration.ofSeconds(1), Duration.ofSeconds(1), 524_288, new ArrayList<>())
                .search(new PlaceSearchQuery("湖畔 博物馆", "上海 市", 7));

        assertEquals(1, calls.get());
        assertEquals(1, result.size());
        assertEquals("B001", result.get(0).sourcePlaceId());
        assertEquals("湖畔 博物馆", result.get(0).name());
        assertEquals("和平路 1 号", result.get(0).address());
        assertEquals("310101", result.get(0).administrativeAreaCode());
        assertEquals("GCJ02", result.get(0).coordinateSystem());
        assertEquals("上海市上海市黄浦区", result.get(0).administrativeArea());
        Map<String, String> query = query(requests.get(0));
        assertEquals("/v3/place/text", requests.get(0).getPath());
        assertEquals("湖畔 博物馆", query.get("keywords"));
        assertEquals("上海 市", query.get("city"));
        assertEquals("true", query.get("citylimit"));
        assertEquals("7", query.get("offset"));
        assertEquals("1", query.get("page"));
        assertEquals("base", query.get("extensions"));
        assertEquals("JSON", query.get("output"));
        assertEquals(KEY, query.get("key"));
    }

    @Test
    void retriesRateLimitAndServerFailureOnlyOnce() throws Exception {
        AtomicInteger rateLimitCalls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        start(exchange -> {
            int call = rateLimitCalls.incrementAndGet();
            respond(exchange, call == 1 ? 429 : 200, call == 1 ? "rate limited" : "{\"status\":\"1\",\"pois\":[]}");
        });
        assertTrue(gateway(Duration.ofSeconds(1), Duration.ofSeconds(1), 524_288, delays)
                .search(new PlaceSearchQuery("private-keyword", null, 10)).isEmpty());
        assertEquals(2, rateLimitCalls.get());
        assertEquals(List.of(50L), delays);

        stopServer();
        server = null;
        AtomicInteger serverCalls = new AtomicInteger();
        delays.clear();
        start(exchange -> {
            int call = serverCalls.incrementAndGet();
            respond(exchange, call == 1 ? 503 : 200, call == 1 ? "unavailable" : "{\"status\":\"1\",\"pois\":[]}");
        });
        assertTrue(gateway(Duration.ofSeconds(1), Duration.ofSeconds(1), 524_288, delays)
                .search(new PlaceSearchQuery("private-keyword", null, 10)).isEmpty());
        assertEquals(2, serverCalls.get());
        assertEquals(List.of(50L), delays);
    }

    @Test
    void doesNotRetryDeterministicFailuresOrEmptyResults() throws Exception {
        for (Fixture fixture : List.of(
                new Fixture(400, "bad request", ProviderFailure.PROVIDER_UNAVAILABLE, false),
                new Fixture(200, "{\"status\":\"0\",\"info\":\"private provider detail\"}", ProviderFailure.PROVIDER_UNAVAILABLE, false),
                new Fixture(200, "not-json private provider body", ProviderFailure.INVALID_PROVIDER_RESPONSE, false),
                new Fixture(200, "{\"status\":\"1\",\"pois\":[]}", null, true)
        )) {
            AtomicInteger calls = new AtomicInteger();
            start(exchange -> {
                calls.incrementAndGet();
                respond(exchange, fixture.status(), fixture.body());
            });
            var gateway = gateway(Duration.ofSeconds(1), Duration.ofSeconds(1), 524_288, new ArrayList<>());
            if (fixture.empty()) {
                assertTrue(gateway.search(new PlaceSearchQuery("private-keyword", null, 10)).isEmpty());
            } else {
                AmapProviderException exception = assertThrows(AmapProviderException.class,
                        () -> gateway.search(new PlaceSearchQuery("private-keyword", null, 10)));
                assertEquals(fixture.failure(), exception.failure());
                assertFalse(exception.getMessage().contains(KEY));
                assertFalse(exception.getMessage().contains("private-keyword"));
                assertFalse(exception.getMessage().contains("private provider"));
            }
            assertEquals(1, calls.get());
            stopServer();
            server = null;
        }
    }

    @Test
    void rejectsOversizedResponseWithoutRetry() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        start(exchange -> {
            calls.incrementAndGet();
            respond(exchange, 200, "x".repeat(2_000));
        });

        AmapProviderException exception = assertThrows(AmapProviderException.class,
                () -> gateway(Duration.ofSeconds(1), Duration.ofSeconds(1), 1_024, new ArrayList<>())
                        .search(new PlaceSearchQuery("private-keyword", null, 10)));

        assertEquals(ProviderFailure.INVALID_PROVIDER_RESPONSE, exception.failure());
        assertEquals(1, calls.get());
    }

    @Test
    void retriesTimeoutAtMostOnceAndKeepsFailureSafe() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        start(exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(200);
                respond(exchange, 200, "{\"status\":\"1\",\"pois\":[]}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 客户端按预算关闭连接是本场景的预期行为。
            }
        });

        AmapProviderException exception = assertThrows(AmapProviderException.class,
                () -> gateway(Duration.ofMillis(50), Duration.ofMillis(50), 524_288, delays)
                        .search(new PlaceSearchQuery("private-keyword", null, 10)));

        assertEquals(ProviderFailure.PROVIDER_TIMEOUT, exception.failure());
        assertEquals(2, calls.get());
        assertEquals(List.of(50L), delays);
        assertEquals("PROVIDER_TIMEOUT", exception.getMessage());
    }

    @Test
    void doesNotStartARetryThatCouldExceedTheSevenSecondCallBudget() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        List<Long> delays = new ArrayList<>();
        start(exchange -> {
            calls.incrementAndGet();
            try {
                Thread.sleep(3_500);
                respond(exchange, 200, "{\"status\":\"1\",\"pois\":[]}");
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
                // 客户端超时关闭连接是本场景的预期行为。
            }
        });

        AmapProviderException exception = assertThrows(AmapProviderException.class,
                () -> gateway(Duration.ofSeconds(1), Duration.ofSeconds(3), 524_288, delays)
                        .search(new PlaceSearchQuery("private-keyword", null, 10)));

        assertEquals(ProviderFailure.PROVIDER_TIMEOUT, exception.failure());
        assertEquals(1, calls.get());
        assertTrue(delays.isEmpty());
    }

    @Test
    void retriesConnectionFailureOnlyOnceAndKeepsFailureSafe() throws Exception {
        int unavailablePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unavailablePort = socket.getLocalPort();
        }
        List<Long> delays = new ArrayList<>();
        AmapWebServiceProperties properties = new AmapWebServiceProperties(
                URI.create("http://127.0.0.1:" + unavailablePort), KEY,
                Duration.ofMillis(50), Duration.ofMillis(50), 4, 524_288);
        AmapProviderException exception = assertThrows(AmapProviderException.class,
                () -> new AmapPlaceSearchGateway(properties, new ObjectMapper(), delays::add, () -> 0L)
                        .search(new PlaceSearchQuery("private-keyword", null, 10)));

        assertEquals(ProviderFailure.PROVIDER_UNAVAILABLE, exception.failure());
        assertEquals(List.of(50L), delays);
        assertEquals("PROVIDER_UNAVAILABLE", exception.getMessage());
    }

    @Test
    void gatewayLogsNeverContainRequestSecretsOrProviderBody() throws Exception {
        ListAppender<ILoggingEvent> logs = capture(AmapHttpExecutor.class);
        start(exchange -> respond(exchange, 400, "private-provider-body"));

        assertThrows(AmapProviderException.class,
                () -> gateway(Duration.ofSeconds(1), Duration.ofSeconds(1), 524_288, new ArrayList<>())
                        .search(new PlaceSearchQuery("private-keyword", null, 10)));

        String rendered = logs.list.stream().map(ILoggingEvent::getFormattedMessage)
                .reduce("", (left, right) -> left + "\n" + right);
        for (String forbidden : List.of(KEY, "private-keyword", "private-provider-body", "?key=")) {
            assertFalse(rendered.contains(forbidden), forbidden);
        }
    }

    private AmapPlaceSearchGateway gateway(
            Duration connectTimeout, Duration responseTimeout, int maximum, List<Long> delays
    ) {
        AmapWebServiceProperties properties = new AmapWebServiceProperties(baseUrl(), KEY, connectTimeout,
                responseTimeout, 4, maximum);
        return new AmapPlaceSearchGateway(properties, new ObjectMapper(), delays::add, () -> 0L);
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
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
    }

    private URI baseUrl() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort());
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
            values.put(decode(parts[0]), decode(parts.length == 2 ? parts[1] : ""));
        });
        return values;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static ListAppender<ILoggingEvent> capture(Class<?> source) {
        Logger logger = (Logger) LoggerFactory.getLogger(source);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private record Fixture(int status, String body, ProviderFailure failure, boolean empty) {
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
