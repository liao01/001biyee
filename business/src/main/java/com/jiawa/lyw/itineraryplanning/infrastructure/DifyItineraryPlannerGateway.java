package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itineraryplanning.application.ItineraryPlannerGateway;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class DifyItineraryPlannerGateway implements ItineraryPlannerGateway {
    private final DifyItineraryPlanningProperties properties;
    private final ObjectMapper objectMapper;
    private final RevisionContractParser contractParser;
    private final RestClient restClient;

    public DifyItineraryPlannerGateway(
            DifyItineraryPlanningProperties properties,
            ObjectMapper objectMapper,
            RevisionContractParser contractParser
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper.copy();
        this.contractParser = contractParser;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder()
                .baseUrl(stripTrailingSlash(properties.baseUrl().toString()))
                .requestFactory(requestFactory)
                .build();
    }

    @Override
    public Generation generate(
            long actorMemberId,
            PlanningModels.RequestDraft request,
            ItineraryModels.Snapshot snapshot
    ) {
        if (actorMemberId <= 0 || request == null || snapshot == null) {
            throw new PlanningException(PlanningError.INVALID_REQUEST, "规划调用参数无效");
        }
        DifyWorkflowModels.RunRequest runRequest = new DifyWorkflowModels.RunRequest(
                Map.of(
                        "planning_request_json", json(sanitizedRequest(request)),
                        "itinerary_snapshot_json", json(sanitizedSnapshot(snapshot)),
                        "contract_version", properties.contractVersion()
                ),
                "blocking",
                pseudonymousUser(actorMemberId)
        );
        DifyWorkflowModels.RawResponse response = execute(runRequest);
        if (response.status() == 429) {
            throw new PlanningException(PlanningError.PROVIDER_RATE_LIMITED, "AI 规划服务请求过多");
        }
        if (response.status() < 200 || response.status() >= 300) {
            throw providerUnavailable();
        }
        return parseResponse(response.body());
    }

    private DifyWorkflowModels.RawResponse execute(DifyWorkflowModels.RunRequest request) {
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                return executeOnce(request);
            } catch (RestClientException exception) {
                if (attempt == 0 && isConnectionEstablishmentFailure(exception)) {
                    continue;
                }
                if (isTimeout(exception)) {
                    throw providerTimeout();
                }
                throw providerUnavailable();
            }
        }
        throw providerUnavailable();
    }

    private DifyWorkflowModels.RawResponse executeOnce(DifyWorkflowModels.RunRequest request) {
        return restClient.post()
                .uri("/v1/workflows/run")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((ignoredRequest, response) -> new DifyWorkflowModels.RawResponse(
                        response.getStatusCode().value(),
                        readBounded(response.getBody(), properties.maxResponseBytes())
                ));
    }

    private Generation parseResponse(byte[] bytes) {
        try {
            JsonNode root = objectMapper.readTree(bytes);
            JsonNode data = root == null ? null : root.get("data");
            if (data == null || !data.isObject()
                    || !"succeeded".equals(text(data, "status"))) {
                throw providerUnavailable();
            }
            JsonNode outputs = data.get("outputs");
            if (outputs == null || !outputs.isObject()) {
                throw invalidContract();
            }
            String revisionJson = text(outputs, "revision_json");
            if (revisionJson == null) {
                throw invalidContract();
            }
            PlanningModels.CandidateProposal proposal = contractParser.parse(revisionJson);
            if (proposal.knowledgeReferenceIds().isEmpty() && outputs.has("knowledge_reference_ids")) {
                JsonNode references = outputs.get("knowledge_reference_ids");
                if (!references.isArray()) {
                    throw invalidContract();
                }
                List<String> ids = new ArrayList<>();
                for (JsonNode reference : references) {
                    if (!reference.isTextual()) {
                        throw invalidContract();
                    }
                    ids.add(reference.textValue());
                }
                proposal = new PlanningModels.CandidateProposal(
                        proposal.contractVersion(), proposal.summary(), proposal.operations(), ids
                );
            }
            String runId = text(root, "workflow_run_id");
            if (runId == null) {
                runId = text(data, "id");
            }
            if (runId == null || runId.length() > 128) {
                throw invalidContract();
            }
            long elapsedMillis = data.path("elapsed_time").isNumber()
                    ? Math.max(0, Math.round(data.path("elapsed_time").doubleValue() * 1000)) : 0;
            Long totalTokens = data.path("total_tokens").canConvertToLong()
                    ? Math.max(0, data.path("total_tokens").longValue()) : null;
            return new Generation(
                    proposal,
                    runId,
                    boundedOptionalText(outputs, "model_name", 128),
                    boundedOptionalText(outputs, "workflow_version", 64),
                    elapsedMillis,
                    totalTokens
            );
        } catch (PlanningException exception) {
            throw exception;
        } catch (RuntimeException | IOException exception) {
            throw invalidContract();
        }
    }

    private Map<String, Object> sanitizedRequest(PlanningModels.RequestDraft request) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("schema_version", request.schemaVersion());
        result.put("itinerary_id", request.itineraryId());
        result.put("start_date", request.startDate());
        result.put("end_date", request.endDate());
        result.put("budget_amount", request.budgetAmount());
        result.put("budget_currency", request.budgetCurrency().getCurrencyCode());
        result.put("party_size", request.partySize());
        result.put("preferences", Map.of(
                "pace", request.preferences().pace().name(),
                "tags", request.preferences().tags().stream().map(Enum::name).sorted().toList(),
                "notes", request.preferences().notes() == null ? "" : request.preferences().notes()
        ));
        result.put("destinations", request.destinations().stream().map(destination -> Map.of(
                "name", destination.name(),
                "country_code", destination.countryCode() == null ? "" : destination.countryCode(),
                "time_zone", destination.timeZone().getId()
        )).toList());
        return result;
    }

    private Map<String, Object> sanitizedSnapshot(ItineraryModels.Snapshot snapshot) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("itinerary_id", snapshot.id());
        result.put("version", snapshot.version());
        result.put("title", snapshot.title());
        result.put("start_date", snapshot.startDate());
        result.put("end_date", snapshot.endDate());
        result.put("time_zone", snapshot.timeZone().getId());
        result.put("base_currency", snapshot.baseCurrency().getCurrencyCode());
        result.put("status", snapshot.status().name());
        result.put("destinations", snapshot.destinations().stream().map(destination -> Map.of(
                "id", destination.id(),
                "name", destination.name(),
                "country_code", destination.countryCode() == null ? "" : destination.countryCode(),
                "time_zone", destination.timeZone().getId(),
                "position", destination.position()
        )).toList());
        List<Map<String, Object>> days = new ArrayList<>();
        for (ItineraryModels.Day day : snapshot.days()) {
            List<Map<String, Object>> items = new ArrayList<>();
            for (ItineraryModels.Item item : day.items()) {
                if (!item.deleted()) {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("id", item.id());
                    value.put("title", item.title());
                    value.put("place_name", item.placeName());
                    value.put("start_time", item.startTime());
                    value.put("end_time", item.endTime());
                    value.put("estimated_cost", item.estimatedCost());
                    value.put("position", item.position());
                    items.add(value);
                }
            }
            days.add(Map.of("id", day.id(), "date", day.date(), "items", items));
        }
        result.put("days", days);
        return result;
    }

    private String pseudonymousUser(long actorMemberId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(
                    properties.userHashKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"
            ));
            byte[] digest = mac.doFinal(Long.toString(actorMemberId).getBytes(StandardCharsets.US_ASCII));
            return "travel-" + HexFormat.of().formatHex(digest, 0, 16);
        } catch (java.security.GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot create Dify user pseudonym", exception);
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new PlanningException(PlanningError.INVALID_REQUEST, "规划输入无法序列化");
        }
    }

    private static byte[] readBounded(InputStream input, int maximum) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(maximum, 8192));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximum) {
                throw new IOException("Dify response exceeds configured limit");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isTextual() && !value.textValue().isBlank()
                ? value.textValue() : null;
    }

    private static String boundedOptionalText(JsonNode node, String field, int maximum) {
        String value = text(node, field);
        if (value != null && value.length() > maximum) {
            throw invalidContract();
        }
        return value;
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static boolean isConnectionEstablishmentFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof ConnectException
                    || current instanceof NoRouteToHostException
                    || current instanceof UnknownHostException) {
                return true;
            }
            if (current instanceof SocketTimeoutException
                    && current.getMessage() != null
                    && current.getMessage().toLowerCase(Locale.ROOT).contains("connect")) {
                return true;
            }
        }
        return false;
    }

    private static boolean isTimeout(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof SocketTimeoutException) {
                return true;
            }
        }
        return false;
    }

    private static PlanningException providerUnavailable() {
        return new PlanningException(PlanningError.PROVIDER_UNAVAILABLE, "AI 规划服务暂不可用");
    }

    private static PlanningException providerTimeout() {
        return new PlanningException(PlanningError.PROVIDER_TIMEOUT, "AI 规划服务响应超时");
    }

    private static PlanningException invalidContract() {
        return new PlanningException(PlanningError.INVALID_CONTRACT, "AI 建议契约无效");
    }
}
