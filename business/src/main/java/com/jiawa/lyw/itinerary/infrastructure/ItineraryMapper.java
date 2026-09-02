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

    ItineraryRows.ItineraryRow findItinerary(@Param("itineraryId") long itineraryId);

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
