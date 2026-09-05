package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerary.domain.ItineraryRules;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

public final class ItineraryCommands {
    private static final Pattern REVISION_OPERATION_KEY = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._-]{0,99}"
    );
    private ItineraryCommands() {
    }

    public record CommandEnvelope<T>(UUID commandId, long expectedVersion, T payload) {
        public CommandEnvelope {
            if (commandId == null || expectedVersion < 0 || payload == null) {
                throw invalidItinerary();
            }
        }
    }

    public record DestinationInput(
            Long id,
            String name,
            String countryCode,
            String timeZone
    ) {
        public DestinationInput {
            if (id != null && id <= 0) {
                throw invalidDestination();
            }
            name = requiredText(name, 100, ItineraryError.INVALID_DESTINATION, "目的地信息无效");
            ItineraryRules.requireCountryCode(countryCode);
            try {
                ItineraryRules.requireTimezone(timeZone);
            } catch (ItineraryException exception) {
                throw invalidDestination();
            }
        }
    }

    public record CreateItinerary(
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency,
            List<DestinationInput> destinations
    ) {
        public CreateItinerary {
            title = requiredText(title, 100, ItineraryError.INVALID_ITINERARY, "行程信息无效");
            ItineraryRules.dates(startDate, endDate);
            ItineraryRules.requireTimezone(timeZone);
            ItineraryRules.requireCurrency(baseCurrency);
            destinations = copyDestinations(destinations);
            if (destinations.stream().anyMatch(destination -> destination.id() != null)) {
                throw invalidDestination();
            }
        }
    }

    public record UpdateOverview(
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency
    ) {
        public UpdateOverview {
            if (title == null && startDate == null && endDate == null
                    && timeZone == null && baseCurrency == null) {
                throw invalidItinerary();
            }
            if (title != null) {
                title = requiredText(title, 100, ItineraryError.INVALID_ITINERARY, "行程信息无效");
            }
            if ((startDate == null) != (endDate == null)) {
                throw invalidItinerary();
            }
            if (startDate != null) {
                ItineraryRules.dates(startDate, endDate);
            }
            if (timeZone != null) {
                ItineraryRules.requireTimezone(timeZone);
            }
            if (baseCurrency != null) {
                ItineraryRules.requireCurrency(baseCurrency);
            }
        }
    }

    public record ReplaceDestinations(List<DestinationInput> destinations) {
        public ReplaceDestinations {
            destinations = copyDestinations(destinations);
        }
    }

    public record AddItem(
            long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost
    ) {
        public AddItem {
            if (dayId <= 0) {
                throw invalidItem();
            }
            title = requiredText(title, 120, ItineraryError.INVALID_ITEM, "安排信息无效");
            placeName = optionalText(placeName, 200, ItineraryError.INVALID_ITEM, "安排信息无效");
            notes = optionalText(notes, 2000, ItineraryError.INVALID_ITEM, "安排信息无效");
            validateItemValues(startTime, endTime, estimatedCost);
        }
    }

    public record UpdateItem(
            long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost
    ) {
        public UpdateItem {
            if (dayId <= 0) {
                throw invalidItem();
            }
            title = requiredText(title, 120, ItineraryError.INVALID_ITEM, "安排信息无效");
            placeName = optionalText(placeName, 200, ItineraryError.INVALID_ITEM, "安排信息无效");
            notes = optionalText(notes, 2000, ItineraryError.INVALID_ITEM, "安排信息无效");
            validateItemValues(startTime, endTime, estimatedCost);
        }
    }

    public record DeleteItem() {
    }

    public record ReorderItems(long dayId, List<Long> itemIds) {
        public ReorderItems {
            if (dayId <= 0 || itemIds == null || itemIds.stream().anyMatch(Objects::isNull)
                    || itemIds.stream().anyMatch(id -> id <= 0)
                    || new HashSet<>(itemIds).size() != itemIds.size()) {
                throw invalidItem();
            }
            itemIds = List.copyOf(itemIds);
        }
    }

    public record TransitionStatus(ItineraryStatus toStatus) {
        public TransitionStatus {
            if (toStatus == null) {
                throw invalidItinerary();
            }
        }
    }

    public sealed interface RevisionOperation permits RevisionAddItem, RevisionUpdateItem,
            RevisionDeleteItem, RevisionReorderItems {
        String operationKey();
    }

    public record RevisionAddItem(
            String operationKey,
            long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost
    ) implements RevisionOperation {
        public RevisionAddItem {
            operationKey = revisionKey(operationKey);
            if (dayId <= 0) {
                throw invalidItem();
            }
            title = requiredText(title, 120, ItineraryError.INVALID_ITEM, "安排信息无效");
            placeName = optionalText(placeName, 200, ItineraryError.INVALID_ITEM, "安排信息无效");
            notes = optionalText(notes, 2000, ItineraryError.INVALID_ITEM, "安排信息无效");
            validateItemValues(startTime, endTime, estimatedCost);
        }
    }

    public record RevisionUpdateItem(
            String operationKey,
            long targetItemId,
            long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost
    ) implements RevisionOperation {
        public RevisionUpdateItem {
            operationKey = revisionKey(operationKey);
            if (targetItemId <= 0 || dayId <= 0) {
                throw invalidItem();
            }
            title = requiredText(title, 120, ItineraryError.INVALID_ITEM, "安排信息无效");
            placeName = optionalText(placeName, 200, ItineraryError.INVALID_ITEM, "安排信息无效");
            notes = optionalText(notes, 2000, ItineraryError.INVALID_ITEM, "安排信息无效");
            validateItemValues(startTime, endTime, estimatedCost);
        }
    }

    public record RevisionDeleteItem(String operationKey, long targetItemId)
            implements RevisionOperation {
        public RevisionDeleteItem {
            operationKey = revisionKey(operationKey);
            if (targetItemId <= 0) {
                throw invalidItem();
            }
        }
    }

    public record RevisionReorderItems(
            String operationKey,
            long dayId,
            List<RevisionItemReference> itemReferences
    ) implements RevisionOperation {
        public RevisionReorderItems {
            operationKey = revisionKey(operationKey);
            if (dayId <= 0 || itemReferences == null
                    || itemReferences.stream().anyMatch(Objects::isNull)) {
                throw invalidItem();
            }
            itemReferences = List.copyOf(itemReferences);
            if (new HashSet<>(itemReferences).size() != itemReferences.size()) {
                throw invalidItem();
            }
        }
    }

    public record RevisionItemReference(Long existingItemId, String addedByOperationKey) {
        public RevisionItemReference {
            if ((existingItemId == null) == (addedByOperationKey == null)
                    || (existingItemId != null && existingItemId <= 0)) {
                throw invalidItem();
            }
            if (addedByOperationKey != null) {
                addedByOperationKey = revisionKey(addedByOperationKey);
            }
        }

        public static RevisionItemReference existing(long itemId) {
            return new RevisionItemReference(itemId, null);
        }

        public static RevisionItemReference addedBy(String operationKey) {
            return new RevisionItemReference(null, operationKey);
        }
    }

    public record ApplyRevision(List<RevisionOperation> operations) {
        public ApplyRevision {
            if (operations == null || operations.isEmpty()
                    || operations.size() > 80
                    || operations.stream().anyMatch(Objects::isNull)) {
                throw invalidItinerary();
            }
            operations = List.copyOf(operations);
            if (operations.stream().map(RevisionOperation::operationKey).distinct().count()
                    != operations.size()) {
                throw invalidItinerary();
            }
        }
    }

    public record CommandResult(
            long itineraryId,
            Long itemId,
            long version,
            boolean replayed
    ) {
    }

    public static void assertCreateEnvelope(CommandEnvelope<?> envelope) {
        if (envelope == null || envelope.expectedVersion() != 0) {
            throw invalidItinerary();
        }
    }

    public static void assertExistingEnvelope(CommandEnvelope<?> envelope) {
        if (envelope == null || envelope.expectedVersion() < 1) {
            throw invalidItinerary();
        }
    }

    private static List<DestinationInput> copyDestinations(List<DestinationInput> destinations) {
        if (destinations == null || destinations.isEmpty() || destinations.stream().anyMatch(Objects::isNull)) {
            throw invalidDestination();
        }
        List<DestinationInput> copy = List.copyOf(destinations);
        List<Long> ids = copy.stream().map(DestinationInput::id).filter(Objects::nonNull).toList();
        if (new HashSet<>(ids).size() != ids.size()) {
            throw invalidDestination();
        }
        return copy;
    }

    private static void validateItemValues(
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal estimatedCost
    ) {
        try {
            ItineraryRules.assertTimeRange(startTime, endTime);
        } catch (ItineraryException exception) {
            throw invalidItem();
        }
        if (estimatedCost != null
                && (estimatedCost.signum() < 0
                    || estimatedCost.scale() > 2
                    || estimatedCost.precision() - estimatedCost.scale() > 12)) {
            throw invalidItem();
        }
    }

    private static String requiredText(
            String value,
            int maxCodePoints,
            ItineraryError error,
            String message
    ) {
        String normalized = value == null ? null : value.trim();
        if (normalized == null || normalized.isEmpty()
                || normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new ItineraryException(error, message);
        }
        return normalized;
    }

    private static String optionalText(
            String value,
            int maxCodePoints,
            ItineraryError error,
            String message
    ) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.codePointCount(0, normalized.length()) > maxCodePoints) {
            throw new ItineraryException(error, message);
        }
        return normalized;
    }

    private static ItineraryException invalidItinerary() {
        return new ItineraryException(ItineraryError.INVALID_ITINERARY, "行程信息无效");
    }

    private static ItineraryException invalidDestination() {
        return new ItineraryException(ItineraryError.INVALID_DESTINATION, "目的地信息无效");
    }

    private static ItineraryException invalidItem() {
        return new ItineraryException(ItineraryError.INVALID_ITEM, "安排信息无效");
    }

    private static String revisionKey(String value) {
        if (value == null || !REVISION_OPERATION_KEY.matcher(value).matches()) {
            throw invalidItem();
        }
        return value;
    }
}
