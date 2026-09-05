package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.math.BigDecimal;
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

    void insertItem(NewItem item);

    int updateItem(
            long itineraryId,
            long itemId,
            long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost,
            long position,
            Instant now
    );

    int softDeleteItem(long itineraryId, long itemId, Instant now);

    int updateItemPosition(long itineraryId, long itemId, long dayId, long position, Instant now);

    Optional<ItineraryModels.Snapshot> findSnapshot(long itineraryId);

    Optional<ItineraryModels.Snapshot> findSnapshotForUpdate(long itineraryId);

    int updateOverview(
            long itineraryId,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency,
            long expectedVersion,
            long nextVersion,
            Instant now
    );

    int bumpVersion(long itineraryId, long expectedVersion, long nextVersion, Instant now);

    int updateStatus(
            long itineraryId,
            ItineraryStatus status,
            long expectedVersion,
            long nextVersion,
            Instant now
    );

    void deleteEmptyDaysOutside(long itineraryId, LocalDate startDate, LocalDate endDate);

    void deleteDestinations(long itineraryId);

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

    record NewItem(
            long id,
            long itineraryId,
            long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost,
            long position,
            Instant now
    ) {
    }
}
