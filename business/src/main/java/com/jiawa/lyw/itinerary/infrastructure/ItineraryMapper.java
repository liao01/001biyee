package com.jiawa.lyw.itinerary.infrastructure;

import com.jiawa.lyw.itinerary.application.ItineraryRepository;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;

@Mapper
public interface ItineraryMapper {
    void insertCommand(
            @Param("id") long id,
            @Param("commandId") String commandId,
            @Param("memberId") long memberId,
            @Param("operation") String operation,
            @Param("itineraryId") Long itineraryId,
            @Param("expectedVersion") long expectedVersion,
            @Param("requestHash") String requestHash,
            @Param("now") Instant now
    );

    ItineraryRows.CommandRow findCommand(@Param("commandId") String commandId);

    int completeCommand(
            @Param("commandId") String commandId,
            @Param("itineraryId") long itineraryId,
            @Param("itemId") Long itemId,
            @Param("version") long version
    );

    void insertItinerary(ItineraryRepository.NewItinerary itinerary);

    void insertDestination(ItineraryRepository.NewDestination destination);

    void insertDay(ItineraryRepository.NewDay day);

    void insertItem(ItineraryRepository.NewItem item);

    int updateItem(
            @Param("itineraryId") long itineraryId,
            @Param("itemId") long itemId,
            @Param("dayId") long dayId,
            @Param("title") String title,
            @Param("placeName") String placeName,
            @Param("startTime") java.time.LocalTime startTime,
            @Param("endTime") java.time.LocalTime endTime,
            @Param("notes") String notes,
            @Param("estimatedCost") java.math.BigDecimal estimatedCost,
            @Param("position") long position,
            @Param("now") Instant now
    );

    int softDeleteItem(
            @Param("itineraryId") long itineraryId,
            @Param("itemId") long itemId,
            @Param("now") Instant now
    );

    int updateItemPosition(
            @Param("itineraryId") long itineraryId,
            @Param("itemId") long itemId,
            @Param("dayId") long dayId,
            @Param("position") long position,
            @Param("now") Instant now
    );

    ItineraryRows.ItineraryRow findItinerary(@Param("itineraryId") long itineraryId);

    ItineraryRows.ItineraryRow findItineraryForUpdate(@Param("itineraryId") long itineraryId);

    int updateOverview(
            @Param("itineraryId") long itineraryId,
            @Param("title") String title,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate,
            @Param("timeZone") String timeZone,
            @Param("baseCurrency") String baseCurrency,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion,
            @Param("now") Instant now
    );

    int bumpVersion(
            @Param("itineraryId") long itineraryId,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion,
            @Param("now") Instant now
    );

    int updateStatus(
            @Param("itineraryId") long itineraryId,
            @Param("status") ItineraryStatus status,
            @Param("expectedVersion") long expectedVersion,
            @Param("nextVersion") long nextVersion,
            @Param("now") Instant now
    );

    void deleteEmptyDaysOutside(
            @Param("itineraryId") long itineraryId,
            @Param("startDate") java.time.LocalDate startDate,
            @Param("endDate") java.time.LocalDate endDate
    );

    void deleteDestinations(@Param("itineraryId") long itineraryId);

    List<ItineraryRows.DestinationRow> findDestinations(@Param("itineraryId") long itineraryId);

    List<ItineraryRows.DayRow> findDays(@Param("itineraryId") long itineraryId);

    List<ItineraryRows.ItemRow> findItems(@Param("itineraryId") long itineraryId);

    List<ItineraryRows.SummaryRow> findSummaries(
            @Param("ownerMemberId") long ownerMemberId,
            @Param("statuses") List<ItineraryStatus> statuses,
            @Param("cursorUpdatedAt") Instant cursorUpdatedAt,
            @Param("cursorId") Long cursorId,
            @Param("limit") int limit
    );
}
