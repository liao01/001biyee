package com.jiawa.lyw.itinerary.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public final class ItineraryRows {
    private ItineraryRows() {
    }

    public record CommandRow(
            String commandId,
            long memberId,
            String operation,
            String requestHash,
            Long resultItineraryId,
            Long resultItemId,
            Long resultVersion
    ) {
    }

    public record ItineraryRow(
            long id,
            long ownerMemberId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency,
            String status,
            long version
    ) {
    }

    public record DestinationRow(
            long id,
            String name,
            String countryCode,
            String timeZone,
            long position
    ) {
    }

    public record DayRow(long id, LocalDate dayDate) {
    }

    public record ItemRow(
            long id,
            long itineraryDayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost,
            long position,
            Instant deletedAt
    ) {
    }

    public record SummaryRow(
            long id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String primaryDestination,
            String status,
            long version,
            Instant updatedAt
    ) {
    }
}
