package com.jiawa.lyw.itinerary.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;

public final class ItineraryModels {
    private ItineraryModels() {
    }

    public record Destination(
            long id,
            String name,
            String countryCode,
            ZoneId timeZone,
            long position
    ) {
    }

    public record Item(
            long id,
            long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost,
            long position,
            Instant deletedAt
    ) {
        public boolean deleted() {
            return deletedAt != null;
        }

        public boolean timed() {
            return startTime != null && endTime != null;
        }
    }

    public record Day(long id, LocalDate date, List<Item> items) {
        public Day {
            items = List.copyOf(items);
        }
    }

    public record Snapshot(
            long id,
            long ownerMemberId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            ZoneId timeZone,
            Currency baseCurrency,
            ItineraryStatus status,
            long version,
            List<Destination> destinations,
            List<Day> days
    ) {
        public Snapshot {
            destinations = List.copyOf(destinations);
            days = List.copyOf(days);
        }
    }

    public record Summary(
            long id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String primaryDestination,
            ItineraryStatus status,
            long version,
            Instant updatedAt
    ) {
    }

    public record PageSlice<T>(List<T> items, String nextCursor) {
        public PageSlice {
            items = List.copyOf(items);
        }
    }
}
