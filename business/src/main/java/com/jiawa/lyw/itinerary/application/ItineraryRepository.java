package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItineraryRepository {
    void reserveCommand(
            long id,
            UUID commandId,
            long memberId,
            String operation,
            Long itineraryId,
            long expectedVersion,
            String requestHash,
            Instant now
    );

    Optional<StoredCommand> findCommand(UUID commandId);

    int completeCommand(UUID commandId, long itineraryId, Long itemId, long version);

    void insertItinerary(NewItinerary itinerary);

    void insertDestination(NewDestination destination);

    void insertDay(NewDay day);

    Optional<ItineraryModels.Snapshot> findSnapshot(long itineraryId);

    List<ItineraryModels.Summary> findSummaries(
            long ownerMemberId,
            List<ItineraryStatus> statuses,
            Instant cursorUpdatedAt,
            Long cursorId,
            int limit
    );

    record StoredCommand(
            UUID commandId,
            long memberId,
            String operation,
            String requestHash,
            Long resultItineraryId,
            Long resultItemId,
            Long resultVersion
    ) {
    }

    record NewItinerary(
            long id,
            long ownerMemberId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency,
            ItineraryStatus status,
            long version,
            Instant now
    ) {
    }

    record NewDestination(
            long id,
            long itineraryId,
            String name,
            String countryCode,
            String timeZone,
            long position,
            Instant now
    ) {
    }

    record NewDay(
            long id,
            long itineraryId,
            LocalDate date,
            Instant now
    ) {
    }
}
