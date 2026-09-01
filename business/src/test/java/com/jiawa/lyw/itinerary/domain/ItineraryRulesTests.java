package com.jiawa.lyw.itinerary.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItineraryRulesTests {
    @Test
    void validatesTimezoneCurrencyCountryAndInclusiveDateRange() {
        assertEquals(ZoneId.of("Asia/Shanghai"), ItineraryRules.requireTimezone("Asia/Shanghai"));
        assertEquals(Currency.getInstance("CNY"), ItineraryRules.requireCurrency("CNY"));
        assertEquals("CN", ItineraryRules.requireCountryCode("CN"));
        assertEquals(null, ItineraryRules.requireCountryCode(null));
        assertEquals(
                List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3)),
                ItineraryRules.dates(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3))
        );

        assertError(ItineraryError.INVALID_ITINERARY, () -> ItineraryRules.requireTimezone("CST"));
        assertError(ItineraryError.INVALID_ITINERARY, () -> ItineraryRules.requireCurrency("cny"));
        assertError(ItineraryError.INVALID_DESTINATION, () -> ItineraryRules.requireCountryCode("CHN"));
        assertError(
                ItineraryError.INVALID_ITINERARY,
                () -> ItineraryRules.dates(LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 1))
        );
        assertError(
                ItineraryError.INVALID_ITINERARY,
                () -> ItineraryRules.dates(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 2))
        );
        assertEquals(
                366,
                ItineraryRules.dates(LocalDate.of(2026, 1, 1), LocalDate.of(2027, 1, 1)).size()
        );
    }

    @Test
    void shrinkingDatesRejectsOnlyDaysThatStillContainLiveItems() {
        var kept = day(1, LocalDate.of(2026, 9, 1), List.of());
        var removedEmpty = day(2, LocalDate.of(2026, 9, 2), List.of());
        var removedDeleted = day(3, LocalDate.of(2026, 9, 3), List.of(item(30, 3, 9, 10, true)));

        assertDoesNotThrow(() -> ItineraryRules.assertShrinkIsSafe(
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1),
                List.of(kept, removedEmpty, removedDeleted)
        ));

        var removedLive = day(4, LocalDate.of(2026, 9, 4), List.of(item(40, 4, 9, 10, false)));
        assertError(
                ItineraryError.DATE_RANGE_CONTAINS_ITEMS,
                () -> ItineraryRules.assertShrinkIsSafe(
                        LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                        List.of(kept, removedEmpty, removedDeleted, removedLive)
                )
        );
    }

    @Test
    void timeRangesUseHalfOpenIntervalsAndIgnoreUntimedOrDeletedItems() {
        var existing = List.of(
                item(1, 1, 9, 10, false),
                item(2, 1, 12, 13, true),
                untimedItem(3, 1)
        );

        assertDoesNotThrow(() -> ItineraryRules.assertTimeRange(null, null));
        assertDoesNotThrow(() -> ItineraryRules.assertNoOverlap(
                null, LocalTime.of(10, 0), LocalTime.of(11, 0), existing
        ));
        assertDoesNotThrow(() -> ItineraryRules.assertNoOverlap(
                1L, LocalTime.of(9, 30), LocalTime.of(10, 30), existing
        ));

        assertError(
                ItineraryError.INVALID_ITEM,
                () -> ItineraryRules.assertTimeRange(LocalTime.of(9, 0), null)
        );
        assertError(
                ItineraryError.INVALID_ITEM,
                () -> ItineraryRules.assertTimeRange(LocalTime.of(23, 0), LocalTime.of(1, 0))
        );
        assertError(
                ItineraryError.TIME_CONFLICT,
                () -> ItineraryRules.assertNoOverlap(
                        null, LocalTime.of(9, 30), LocalTime.of(10, 30), existing
                )
        );
    }

    @Test
    void orderingRequiresAnExactPermutation() {
        assertDoesNotThrow(() -> ItineraryRules.assertPermutation(Set.of(10L, 20L), List.of(20L, 10L)));
        assertError(
                ItineraryError.INVALID_ITEM,
                () -> ItineraryRules.assertPermutation(Set.of(10L, 20L), List.of(10L))
        );
        assertError(
                ItineraryError.INVALID_ITEM,
                () -> ItineraryRules.assertPermutation(Set.of(10L, 20L), List.of(10L, 10L))
        );
        assertError(
                ItineraryError.INVALID_ITEM,
                () -> ItineraryRules.assertPermutation(Set.of(10L, 20L), List.of(10L, 30L))
        );
    }

    @Test
    void lifecycleMatchesTheApprovedGraphAndPlanningMinimum() {
        assertEquals(
                Set.of(ItineraryStatus.PLANNED, ItineraryStatus.CANCELLED, ItineraryStatus.ARCHIVED),
                ItineraryRules.allowedTransitions(ItineraryStatus.DRAFT)
        );
        assertEquals(
                Set.of(ItineraryStatus.DRAFT, ItineraryStatus.IN_PROGRESS,
                        ItineraryStatus.CANCELLED, ItineraryStatus.ARCHIVED),
                ItineraryRules.allowedTransitions(ItineraryStatus.PLANNED)
        );
        assertEquals(Set.of(), ItineraryRules.allowedTransitions(ItineraryStatus.ARCHIVED));

        var incomplete = snapshot(ItineraryStatus.DRAFT, List.of(day(1, LocalDate.of(2026, 9, 1), List.of())));
        assertFalse(ItineraryRules.meetsPlanningMinimum(incomplete));
        assertError(
                ItineraryError.INVALID_STATUS_TRANSITION,
                () -> ItineraryRules.assertTransition(incomplete, ItineraryStatus.PLANNED)
        );

        var complete = snapshot(
                ItineraryStatus.DRAFT,
                List.of(day(1, LocalDate.of(2026, 9, 1), List.of(item(1, 1, 9, 10, false))))
        );
        assertTrue(ItineraryRules.meetsPlanningMinimum(complete));
        assertDoesNotThrow(() -> ItineraryRules.assertTransition(complete, ItineraryStatus.PLANNED));
        assertError(
                ItineraryError.INVALID_STATUS_TRANSITION,
                () -> ItineraryRules.assertTransition(complete, ItineraryStatus.COMPLETED)
        );
    }

    @Test
    void suggestionsUseItineraryLocalDateWithoutChangingStatus() {
        var draft = snapshot(
                ItineraryStatus.DRAFT,
                List.of(day(1, LocalDate.of(2026, 9, 1), List.of(item(1, 1, 9, 10, false))))
        );
        assertEquals(Optional.of(ItineraryStatus.PLANNED),
                ItineraryRules.suggestedStatus(draft, LocalDate.of(2026, 8, 31)));

        var planned = snapshot(ItineraryStatus.PLANNED, draft.days());
        assertEquals(Optional.empty(),
                ItineraryRules.suggestedStatus(planned, LocalDate.of(2026, 8, 31)));
        assertEquals(Optional.of(ItineraryStatus.IN_PROGRESS),
                ItineraryRules.suggestedStatus(planned, LocalDate.of(2026, 9, 1)));

        var inProgress = snapshot(ItineraryStatus.IN_PROGRESS, draft.days());
        assertEquals(Optional.empty(),
                ItineraryRules.suggestedStatus(inProgress, LocalDate.of(2026, 9, 1)));
        assertEquals(Optional.of(ItineraryStatus.COMPLETED),
                ItineraryRules.suggestedStatus(inProgress, LocalDate.of(2026, 9, 4)));
        assertEquals(ItineraryStatus.IN_PROGRESS, inProgress.status());
    }

    private static ItineraryModels.Snapshot snapshot(
            ItineraryStatus status, List<ItineraryModels.Day> days
    ) {
        return new ItineraryModels.Snapshot(
                100, 200, "IT-TEST-#17-上海行程",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3),
                ZoneId.of("Asia/Shanghai"), Currency.getInstance("CNY"), status, 1,
                List.of(new ItineraryModels.Destination(
                        10, "上海", "CN", ZoneId.of("Asia/Shanghai"), 1024
                )),
                days
        );
    }

    private static ItineraryModels.Day day(
            long id, LocalDate date, List<ItineraryModels.Item> items
    ) {
        return new ItineraryModels.Day(id, date, items);
    }

    private static ItineraryModels.Item item(
            long id, long dayId, int startHour, int endHour, boolean deleted
    ) {
        return new ItineraryModels.Item(
                id, dayId, "安排", "地点", LocalTime.of(startHour, 0),
                LocalTime.of(endHour, 0), "备注", new BigDecimal("10.00"),
                1024, deleted ? Instant.parse("2026-09-01T00:00:00Z") : null
        );
    }

    private static ItineraryModels.Item untimedItem(long id, long dayId) {
        return new ItineraryModels.Item(
                id, dayId, "自由活动", null, null, null, null,
                null, 2048, null
        );
    }

    private static void assertError(ItineraryError error, Runnable action) {
        var exception = assertThrows(ItineraryException.class, action::run);
        assertEquals(error, exception.error());
    }
}
