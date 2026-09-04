package com.jiawa.lyw.itineraryplanning.infrastructure;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jiawa.lyw.itineraryplanning.domain.PlanningError;
import com.jiawa.lyw.itineraryplanning.domain.PlanningException;
import com.jiawa.lyw.itineraryplanning.domain.PlanningModels;
import com.jiawa.lyw.itineraryplanning.domain.RevisionOperationType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

public final class RevisionContractParser {
    private static final Set<String> ROOT_FIELDS = Set.of(
            "contract_version", "summary", "operations", "knowledge_reference_ids"
    );
    private static final Set<String> ITEM_FIELDS = Set.of(
            "date", "title", "place_name", "start_time", "end_time", "notes", "estimated_cost"
    );
    private static final Set<String> REFERENCE_FIELDS = Set.of(
            "existing_item_id", "added_by_operation_key"
    );

    private final ObjectMapper objectMapper;
    private final String expectedContractVersion;

    public RevisionContractParser(ObjectMapper objectMapper, String expectedContractVersion) {
        this.objectMapper = objectMapper.copy();
        this.expectedContractVersion = expectedContractVersion;
    }

    public PlanningModels.CandidateProposal parse(String json) {
        try {
            if (json == null || json.isBlank() || json.length() > 1_000_000) {
                throw invalidContract();
            }
            JsonNode root = objectMapper.readTree(json);
            requireObject(root, ROOT_FIELDS, false);
            if (!root.has("contract_version") || !root.has("summary") || !root.has("operations")) {
                throw invalidContract();
            }
            String contractVersion = requiredText(root, "contract_version");
            if (!expectedContractVersion.equals(contractVersion)) {
                throw invalidContract();
            }
            String summary = requiredText(root, "summary");
            JsonNode operationsNode = requiredArray(root, "operations");
            List<PlanningModels.RevisionOperation> operations = new ArrayList<>();
            for (JsonNode operation : operationsNode) {
                operations.add(parseOperation(operation));
            }
            List<String> knowledgeReferences = root.has("knowledge_reference_ids")
                    ? strings(requiredArray(root, "knowledge_reference_ids")) : List.of();
            return new PlanningModels.CandidateProposal(
                    contractVersion, summary, operations, knowledgeReferences
            );
        } catch (PlanningException exception) {
            throw exception;
        } catch (JsonProcessingException | RuntimeException exception) {
            throw invalidContract();
        }
    }

    private PlanningModels.RevisionOperation parseOperation(JsonNode node) {
        if (node == null || !node.isObject()) {
            throw invalidContract();
        }
        RevisionOperationType type;
        try {
            type = RevisionOperationType.valueOf(requiredText(node, "type"));
        } catch (IllegalArgumentException exception) {
            throw invalidContract();
        }
        String key = requiredText(node, "operation_key");
        String summary = requiredText(node, "summary");
        return switch (type) {
            case ADD_ITEM -> {
                requireObject(node, Set.of("operation_key", "type", "summary", "item"));
                yield new PlanningModels.AddItemOperation(key, summary, parseItem(node.get("item")));
            }
            case UPDATE_ITEM -> {
                requireObject(node, Set.of(
                        "operation_key", "type", "summary", "target_item_id", "item"
                ));
                yield new PlanningModels.UpdateItemOperation(
                        key, summary, requiredPositiveLong(node, "target_item_id"),
                        parseItem(node.get("item"))
                );
            }
            case DELETE_ITEM -> {
                requireObject(node, Set.of("operation_key", "type", "summary", "target_item_id"));
                yield new PlanningModels.DeleteItemOperation(
                        key, summary, requiredPositiveLong(node, "target_item_id")
                );
            }
            case REORDER_DAY_ITEMS -> {
                requireObject(node, Set.of(
                        "operation_key", "type", "summary", "target_date", "item_references"
                ));
                List<PlanningModels.ItemReference> references = new ArrayList<>();
                for (JsonNode reference : requiredArray(node, "item_references")) {
                    requireObject(reference, REFERENCE_FIELDS, false);
                    boolean existing = present(reference, "existing_item_id");
                    boolean added = present(reference, "added_by_operation_key");
                    if (existing == added) {
                        throw invalidContract();
                    }
                    references.add(existing
                            ? PlanningModels.ItemReference.existing(
                                    requiredPositiveLong(reference, "existing_item_id"))
                            : PlanningModels.ItemReference.addedBy(
                                    requiredText(reference, "added_by_operation_key")));
                }
                yield new PlanningModels.ReorderDayItemsOperation(
                        key, summary, LocalDate.parse(requiredText(node, "target_date")), references
                );
            }
        };
    }

    private PlanningModels.ItemFields parseItem(JsonNode node) {
        requireObject(node, ITEM_FIELDS, false);
        return new PlanningModels.ItemFields(
                LocalDate.parse(requiredText(node, "date")),
                requiredText(node, "title"),
                requiredText(node, "place_name"),
                optionalTime(node, "start_time"),
                optionalTime(node, "end_time"),
                optionalText(node, "notes"),
                optionalDecimal(node, "estimated_cost")
        );
    }

    private static void requireObject(JsonNode node, Set<String> allowed) {
        requireObject(node, allowed, true);
    }

    private static void requireObject(JsonNode node, Set<String> allowed, boolean requireEveryField) {
        if (node == null || !node.isObject()) {
            throw invalidContract();
        }
        Set<String> actual = new HashSet<>();
        Iterator<String> fields = node.fieldNames();
        fields.forEachRemaining(actual::add);
        if (!allowed.containsAll(actual) || (requireEveryField && !actual.equals(allowed))) {
            throw invalidContract();
        }
    }

    private static JsonNode requiredArray(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isArray()) {
            throw invalidContract();
        }
        return value;
    }

    private static String requiredText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isTextual() || value.textValue().isBlank()) {
            throw invalidContract();
        }
        return value.textValue();
    }

    private static String optionalText(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw invalidContract();
        }
        return value.textValue();
    }

    private static LocalTime optionalTime(JsonNode node, String field) {
        String value = optionalText(node, field);
        return value == null ? null : LocalTime.parse(value);
    }

    private static BigDecimal optionalDecimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isNumber()) {
            throw invalidContract();
        }
        return value.decimalValue();
    }

    private static long requiredPositiveLong(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.canConvertToLong() || value.longValue() <= 0) {
            throw invalidContract();
        }
        return value.longValue();
    }

    private static List<String> strings(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode value : array) {
            if (!value.isTextual()) {
                throw invalidContract();
            }
            values.add(value.textValue());
        }
        return List.copyOf(values);
    }

    private static boolean present(JsonNode node, String field) {
        return node.has(field) && !node.get(field).isNull();
    }

    private static PlanningException invalidContract() {
        return new PlanningException(PlanningError.INVALID_CONTRACT, "AI 建议契约无效");
    }
}
