package com.jiawa.lyw.itineraryplanning.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Currency;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class PlanningModels {
    public static final String REQUEST_SCHEMA_V1 = "itinerary-planning-request/v1";
    public static final String REVISION_CONTRACT_V1 = "itinerary-revision/v1";

    private PlanningModels() {
    }

    public enum TravelPace {
        RELAXED,
        BALANCED,
        FAST
    }

    public enum PreferenceTag {
        CULTURE,
        FOOD,
        NATURE,
        SHOPPING,
        FAMILY,
        NIGHTLIFE,
        PHOTOGRAPHY,
        OUTDOORS
    }

    public record DestinationInput(String name, String countryCode, ZoneId timeZone) {
        public DestinationInput {
            name = normalizedRequired(name, 100, "目的地名称无效");
            countryCode = normalizeCountryCode(countryCode);
            if (timeZone == null) {
                throw invalidRequest("目的地时区不能为空");
            }
        }
    }

    public record Preferences(TravelPace pace, Set<PreferenceTag> tags, String notes) {
        public Preferences {
            if (pace == null || tags == null || tags.isEmpty() || tags.stream().anyMatch(java.util.Objects::isNull)) {
                throw invalidRequest("旅行节奏和偏好标签不能为空");
            }
            tags = Set.copyOf(tags);
            notes = normalizedOptional(notes, 1000, "偏好说明过长");
        }
    }

    public record RequestDraft(
            long itineraryId,
            LocalDate startDate,
            LocalDate endDate,
            BigDecimal budgetAmount,
            Currency budgetCurrency,
            int partySize,
            Preferences preferences,
            List<DestinationInput> destinations
    ) {
        public RequestDraft {
            if (itineraryId <= 0) {
                throw invalidRequest("行程编号无效");
            }
            if (startDate == null || endDate == null || endDate.isBefore(startDate)
                    || ChronoUnit.DAYS.between(startDate, endDate) > 365) {
                throw invalidRequest("规划日期范围无效");
            }
            validateMoney(budgetAmount, PlanningError.INVALID_REQUEST, "预算无效");
            if (budgetCurrency == null) {
                throw invalidRequest("预算币种不能为空");
            }
            if (partySize < 1 || partySize > 100) {
                throw invalidRequest("出行人数应在 1 到 100 之间");
            }
            if (preferences == null) {
                throw invalidRequest("偏好不能为空");
            }
            if (destinations == null || destinations.isEmpty() || destinations.size() > 20
                    || destinations.stream().anyMatch(java.util.Objects::isNull)) {
                throw invalidRequest("目的地数量无效");
            }
            destinations = List.copyOf(destinations);
        }

        public String schemaVersion() {
            return REQUEST_SCHEMA_V1;
        }
    }

    public record ItemFields(
            LocalDate date,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost
    ) {
    }

    public sealed interface RevisionOperation permits AddItemOperation, UpdateItemOperation,
            DeleteItemOperation, ReorderDayItemsOperation {
        String operationKey();

        String summary();

        RevisionOperationType type();
    }

    public record AddItemOperation(String operationKey, String summary, ItemFields item)
            implements RevisionOperation {
        @Override
        public RevisionOperationType type() {
            return RevisionOperationType.ADD_ITEM;
        }
    }

    public record UpdateItemOperation(
            String operationKey,
            String summary,
            long targetItemId,
            ItemFields item
    ) implements RevisionOperation {
        @Override
        public RevisionOperationType type() {
            return RevisionOperationType.UPDATE_ITEM;
        }
    }

    public record DeleteItemOperation(String operationKey, String summary, long targetItemId)
            implements RevisionOperation {
        @Override
        public RevisionOperationType type() {
            return RevisionOperationType.DELETE_ITEM;
        }
    }

    public record ReorderDayItemsOperation(
            String operationKey,
            String summary,
            LocalDate date,
            List<ItemReference> itemReferences
    ) implements RevisionOperation {
        public ReorderDayItemsOperation {
            itemReferences = itemReferences == null ? null : List.copyOf(itemReferences);
        }

        @Override
        public RevisionOperationType type() {
            return RevisionOperationType.REORDER_DAY_ITEMS;
        }
    }

    public record ItemReference(Long existingItemId, String addedByOperationKey) {
        public static ItemReference existing(long itemId) {
            return new ItemReference(itemId, null);
        }

        public static ItemReference addedBy(String operationKey) {
            return new ItemReference(null, operationKey);
        }
    }

    public record CandidateProposal(
            String contractVersion,
            String summary,
            List<RevisionOperation> operations,
            List<String> knowledgeReferenceIds
    ) {
        public CandidateProposal {
            operations = operations == null ? null : List.copyOf(operations);
            knowledgeReferenceIds = knowledgeReferenceIds == null ? null : List.copyOf(knowledgeReferenceIds);
        }
    }

    public record ValidatedProposal(
            CandidateProposal proposal,
            BigDecimal projectedCost,
            Map<String, Set<String>> dependencies
    ) {
        public ValidatedProposal {
            Map<String, Set<String>> copied = new LinkedHashMap<>();
            dependencies.forEach((key, value) -> copied.put(key, Set.copyOf(value)));
            dependencies = Map.copyOf(copied);
        }
    }

    static String normalizedRequired(String value, int maxLength, String message) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty() || normalized.length() > maxLength) {
            throw invalidRequest(message);
        }
        return normalized;
    }

    static String normalizedOptional(String value, int maxLength, String message) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidRequest(message);
        }
        return normalized.isEmpty() ? null : normalized;
    }

    static void validateMoney(BigDecimal value, PlanningError error, String message) {
        if (value == null || value.signum() < 0 || value.scale() > 2 || value.precision() - value.scale() > 12) {
            throw new PlanningException(error, message);
        }
    }

    private static String normalizeCountryCode(String countryCode) {
        if (countryCode == null || countryCode.isBlank()) {
            return null;
        }
        String normalized = countryCode.trim().toUpperCase(java.util.Locale.ROOT);
        if (!normalized.matches("[A-Z]{2}")) {
            throw invalidRequest("国家或地区代码无效");
        }
        return normalized;
    }

    private static PlanningException invalidRequest(String message) {
        return new PlanningException(PlanningError.INVALID_REQUEST, message);
    }
}
