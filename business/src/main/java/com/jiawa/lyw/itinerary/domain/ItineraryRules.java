package com.jiawa.lyw.itinerary.domain;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Currency;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class ItineraryRules {
    private static final int MAX_DAYS = 366;
    private static final Set<String> COUNTRY_CODES = Set.of(Locale.getISOCountries());
    private static final Map<ItineraryStatus, Set<ItineraryStatus>> TRANSITIONS = transitions();

    private ItineraryRules() {
    }

    public static ZoneId requireTimezone(String value) {
        if (value == null || value.isBlank() || ZoneId.SHORT_IDS.containsKey(value)) {
            throw invalidItinerary();
        }
        try {
            return ZoneId.of(value);
        } catch (DateTimeException exception) {
            throw invalidItinerary();
        }
    }

    public static Currency requireCurrency(String value) {
        if (value == null || !value.matches("[A-Z]{3}")) {
            throw invalidItinerary();
        }
        try {
            return Currency.getInstance(value);
        } catch (IllegalArgumentException exception) {
            throw invalidItinerary();
        }
    }

    public static String requireCountryCode(String value) {
        if (value == null) {
            return null;
        }
        if (!COUNTRY_CODES.contains(value)) {
            throw new ItineraryException(ItineraryError.INVALID_DESTINATION, "目的地信息无效");
        }
        return value;
    }

    public static List<LocalDate> dates(LocalDate start, LocalDate end) {
        if (start == null || end == null) {
            throw invalidItinerary();
        }
        long span = ChronoUnit.DAYS.between(start, end);
        if (span < 0 || span >= MAX_DAYS) {
            throw invalidItinerary();
        }
        return start.datesUntil(end.plusDays(1)).toList();
    }

    public static void assertShrinkIsSafe(
            LocalDate newStart,
            LocalDate newEnd,
            Collection<ItineraryModels.Day> currentDays
    ) {
        dates(newStart, newEnd);
        boolean excludedDayContainsItems = currentDays.stream()
                .filter(day -> day.date().isBefore(newStart) || day.date().isAfter(newEnd))
                .flatMap(day -> day.items().stream())
                .anyMatch(item -> !item.deleted());
        if (excludedDayContainsItems) {
            throw new ItineraryException(
                    ItineraryError.DATE_RANGE_CONTAINS_ITEMS,
                    "缩短后的日期范围仍包含安排"
            );
        }
    }

    public static void assertTimeRange(LocalTime start, LocalTime end) {
        if (start == null && end == null) {
            return;
        }
        if (start == null || end == null || !end.isAfter(start)) {
            throw new ItineraryException(ItineraryError.INVALID_ITEM, "安排时间无效");
        }
    }

    public static void assertNoOverlap(
            Long ignoredItemId,
            LocalTime start,
            LocalTime end,
            Collection<ItineraryModels.Item> items
    ) {
        assertTimeRange(start, end);
        if (start == null) {
            return;
        }
        boolean overlaps = items.stream()
                .filter(item -> !item.deleted())
                .filter(ItineraryModels.Item::timed)
                .filter(item -> ignoredItemId == null || item.id() != ignoredItemId)
                .anyMatch(item -> start.isBefore(item.endTime()) && item.startTime().isBefore(end));
        if (overlaps) {
            throw new ItineraryException(ItineraryError.TIME_CONFLICT, "安排时间存在冲突");
        }
    }

    public static void assertPermutation(Collection<Long> expected, List<Long> actual) {
        if (expected == null || actual == null
                || expected.size() != actual.size()
                || new HashSet<>(actual).size() != actual.size()
                || !new HashSet<>(expected).equals(new HashSet<>(actual))) {
            throw new ItineraryException(ItineraryError.INVALID_ITEM, "安排顺序无效");
        }
    }

    public static Set<ItineraryStatus> allowedTransitions(ItineraryStatus from) {
        return TRANSITIONS.getOrDefault(from, Set.of());
    }

    public static void assertTransition(
            ItineraryModels.Snapshot snapshot,
            ItineraryStatus to
    ) {
        if (snapshot == null || to == null
                || !allowedTransitions(snapshot.status()).contains(to)
                || (snapshot.status() == ItineraryStatus.DRAFT
                    && to == ItineraryStatus.PLANNED
                    && !meetsPlanningMinimum(snapshot))) {
            throw new ItineraryException(
                    ItineraryError.INVALID_STATUS_TRANSITION,
                    "行程状态转换无效"
            );
        }
    }

    public static boolean meetsPlanningMinimum(ItineraryModels.Snapshot snapshot) {
        if (snapshot == null || snapshot.destinations().isEmpty()) {
            return false;
        }
        try {
            dates(snapshot.startDate(), snapshot.endDate());
            boolean hasLiveItem = false;
            for (ItineraryModels.Day day : snapshot.days()) {
                if (day.date().isBefore(snapshot.startDate()) || day.date().isAfter(snapshot.endDate())) {
                    return false;
                }
                List<ItineraryModels.Item> prior = new ArrayList<>();
                for (ItineraryModels.Item item : day.items()) {
                    if (item.deleted()) {
                        continue;
                    }
                    hasLiveItem = true;
                    assertNoOverlap(item.id(), item.startTime(), item.endTime(), prior);
                    prior.add(item);
                }
            }
            return hasLiveItem;
        } catch (ItineraryException exception) {
            return false;
        }
    }

    public static Optional<ItineraryStatus> suggestedStatus(
            ItineraryModels.Snapshot snapshot,
            LocalDate localToday
    ) {
        if (snapshot == null || localToday == null) {
            return Optional.empty();
        }
        if (snapshot.status() == ItineraryStatus.DRAFT && meetsPlanningMinimum(snapshot)) {
            return Optional.of(ItineraryStatus.PLANNED);
        }
        if (snapshot.status() == ItineraryStatus.PLANNED
                && !localToday.isBefore(snapshot.startDate())
                && !localToday.isAfter(snapshot.endDate())) {
            return Optional.of(ItineraryStatus.IN_PROGRESS);
        }
        if (snapshot.status() == ItineraryStatus.IN_PROGRESS
                && localToday.isAfter(snapshot.endDate())) {
            return Optional.of(ItineraryStatus.COMPLETED);
        }
        return Optional.empty();
    }

    private static Map<ItineraryStatus, Set<ItineraryStatus>> transitions() {
        Map<ItineraryStatus, Set<ItineraryStatus>> transitions = new EnumMap<>(ItineraryStatus.class);
        transitions.put(ItineraryStatus.DRAFT,
                Set.of(ItineraryStatus.PLANNED, ItineraryStatus.CANCELLED, ItineraryStatus.ARCHIVED));
        transitions.put(ItineraryStatus.PLANNED,
                Set.of(ItineraryStatus.DRAFT, ItineraryStatus.IN_PROGRESS,
                        ItineraryStatus.CANCELLED, ItineraryStatus.ARCHIVED));
        transitions.put(ItineraryStatus.IN_PROGRESS,
                Set.of(ItineraryStatus.COMPLETED, ItineraryStatus.CANCELLED));
        transitions.put(ItineraryStatus.COMPLETED, Set.of(ItineraryStatus.ARCHIVED));
        transitions.put(ItineraryStatus.CANCELLED, Set.of(ItineraryStatus.ARCHIVED));
        transitions.put(ItineraryStatus.ARCHIVED, Set.of());
        return Map.copyOf(transitions);
    }

    private static ItineraryException invalidItinerary() {
        return new ItineraryException(ItineraryError.INVALID_ITINERARY, "行程信息无效");
    }
}
