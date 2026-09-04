package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DifyItineraryPlannerGatewayTests {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private HttpServer server;
    private final AtomicReference<String> requestBody = new AtomicReference<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private volatile int responseStatus = 200;
    private volatile byte[] responseBody;

    @BeforeEach
    void startServer() throws IOException {
        responseBody = successResponse().getBytes(StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/workflows/run", this::handle);
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void callsBlockingWorkflowWithPseudonymousUserAndSanitizedKnowledgeInputs() throws Exception {
        var generation = gateway(64 * 1024).generate(42L, request(), snapshot());

        assertEquals("dify-run-42", generation.providerRunId());
        assertEquals(1, generation.proposal().operations().size());
        assertTrue(authorization.get().startsWith("Bearer "));
        JsonNode body = objectMapper.readTree(requestBody.get());
        assertEquals("blocking", body.get("response_mode").textValue());
        assertTrue(body.get("user").textValue().startsWith("travel-"));
        assertFalse(body.get("user").textValue().equals("42"));
        assertEquals(Set.of(
                        "planning_request_json", "itinerary_snapshot_json", "contract_version"),
                fieldNames(body.get("inputs")));
        String serialized = body.toString();
        assertFalse(serialized.contains("owner@example.com"));
        assertFalse(serialized.contains("PRIVATE-BOOKING-NOTE"));
        assertFalse(serialized.contains("ownerMemberId"));
    }

    @Test
    void classifiesRateLimitsFailedRunsAndOversizedBodiesWithoutLeakingSecrets() {
        responseStatus = 429;
        assertError(PlanningError.PROVIDER_RATE_LIMITED, () -> gateway(64 * 1024).generate(42, request(), snapshot()));

        responseStatus = 200;
        responseBody = "{\"workflow_run_id\":\"x\",\"data\":{\"status\":\"failed\",\"error\":\"internal\"}}"
                .getBytes(StandardCharsets.UTF_8);
        assertError(PlanningError.PROVIDER_UNAVAILABLE, () -> gateway(64 * 1024).generate(42, request(), snapshot()));

        responseBody = ("{\"padding\":\"" + "x".repeat(2048) + "\"}")
                .getBytes(StandardCharsets.UTF_8);
        assertError(PlanningError.PROVIDER_UNAVAILABLE, () -> gateway(1024).generate(42, request(), snapshot()));
    }

    private DifyItineraryPlannerGateway gateway(int maxBytes) {
        DifyItineraryPlanningProperties properties = new DifyItineraryPlanningProperties(
                URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                "app-secret-key-1234567890",
                "0123456789abcdef0123456789abcdef",
                PlanningModels.REVISION_CONTRACT_V1,
                Duration.ofSeconds(2), Duration.ofSeconds(5), maxBytes, 80
        );
        return new DifyItineraryPlannerGateway(
                properties,
                objectMapper,
                new RevisionContractParser(objectMapper, PlanningModels.REVISION_CONTRACT_V1)
        );
    }

    private void handle(HttpExchange exchange) throws IOException {
        authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
        requestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
        exchange.sendResponseHeaders(responseStatus, responseBody.length);
        exchange.getResponseBody().write(responseBody);
        exchange.close();
    }

    private String successResponse() {
        String revision = """
                {"contract_version":"itinerary-revision/v1","summary":"知识库建议",
                 "operations":[{"operation_key":"add-one","type":"ADD_ITEM","summary":"新增",
                   "item":{"date":"2026-10-02","title":"参观","place_name":"博物馆",
                           "start_time":"09:00:00","end_time":"10:00:00","notes":null,
                           "estimated_cost":0.00}}],
                 "knowledge_reference_ids":["kb:guide:1"]}
                """;
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "workflow_run_id", "dify-run-42",
                    "task_id", "task-1",
                    "data", java.util.Map.of(
                            "status", "succeeded",
                            "outputs", java.util.Map.of("revision_json", revision),
                            "elapsed_time", 0.25,
                            "total_tokens", 321
                    )
            ));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private PlanningModels.RequestDraft request() {
        return new PlanningModels.RequestDraft(
                42, LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
                new BigDecimal("3000.00"), Currency.getInstance("CNY"), 2,
                new PlanningModels.Preferences(
                        PlanningModels.TravelPace.BALANCED,
                        Set.of(PlanningModels.PreferenceTag.CULTURE), "不早起"
                ),
                List.of(new PlanningModels.DestinationInput("杭州", "CN", ZoneId.of("Asia/Shanghai")))
        );
    }

    private ItineraryModels.Snapshot snapshot() {
        return new ItineraryModels.Snapshot(
                42, 77, "杭州", LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3),
                ZoneId.of("Asia/Shanghai"), Currency.getInstance("CNY"), ItineraryStatus.DRAFT, 3,
                List.of(new ItineraryModels.Destination(1, "杭州", "CN", ZoneId.of("Asia/Shanghai"), 1024)),
                List.of(new ItineraryModels.Day(10, LocalDate.of(2026, 10, 2), List.of(
                        new ItineraryModels.Item(
                                100, 10, "已有安排", "西湖", null, null,
                                "PRIVATE-BOOKING-NOTE", BigDecimal.ZERO, 1024, null
                        )
                )))
        );
    }

    private static Set<String> fieldNames(JsonNode node) {
        Set<String> names = new java.util.HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private static void assertError(PlanningError error, Runnable action) {
        assertEquals(error, assertThrows(PlanningException.class, action::run).error());
    }
}
