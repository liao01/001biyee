package com.jiawa.lyw.itinerary.infrastructure;

import com.jiawa.lyw.itinerary.application.ItineraryRepository;
import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

public final class MyBatisItineraryRepository implements ItineraryRepository {
    private final ItineraryMapper mapper;

    public MyBatisItineraryRepository(ItineraryMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void reserveCommand(
            long id,
            UUID commandId,
            long memberId,
            String operation,
            Long itineraryId,
            long expectedVersion,
            String requestHash,
            Instant now
    ) {
        mapper.insertCommand(
                id, commandId.toString(), memberId, operation, itineraryId,
                expectedVersion, requestHash, now
        );
    }

    @Override
    public Optional<StoredCommand> findCommand(UUID commandId) {
        ItineraryRows.CommandRow row = mapper.findCommand(commandId.toString());
        return Optional.ofNullable(row).map(command -> new StoredCommand(
                UUID.fromString(command.commandId()), command.memberId(), command.operation(),
                command.requestHash(), command.resultItineraryId(), command.resultItemId(),
                command.resultVersion()
        ));
    }

    @Override
    public int completeCommand(UUID commandId, long itineraryId, Long itemId, long version) {
        return mapper.completeCommand(commandId.toString(), itineraryId, itemId, version);
    }

    @Override
    public void insertItinerary(NewItinerary itinerary) {
        mapper.insertItinerary(itinerary);
    }

    @Override
    public void insertDestination(NewDestination destination) {
        mapper.insertDestination(destination);
    }

    @Override
    public void insertDay(NewDay day) {
        mapper.insertDay(day);
    }

    @Override
    public void insertItem(NewItem item) {
        mapper.insertItem(item);
    }

    @Override
    public int updateItem(
            long itineraryId,
            long itemId,
            long dayId,
            String title,
            String placeName,
            java.time.LocalTime startTime,
            java.time.LocalTime endTime,
            String notes,
            java.math.BigDecimal estimatedCost,
            long position,
            Instant now
    ) {
        return mapper.updateItem(
                itineraryId, itemId, dayId, title, placeName, startTime, endTime,
                notes, estimatedCost, position, now
        );
    }

    @Override
    public int softDeleteItem(long itineraryId, long itemId, Instant now) {
        return mapper.softDeleteItem(itineraryId, itemId, now);
    }

    @Override
    public int updateItemPosition(
            long itineraryId,
            long itemId,
            long dayId,
            long position,
            Instant now
    ) {
        return mapper.updateItemPosition(itineraryId, itemId, dayId, position, now);
    }

    @Override
    public void upsertItemPlace(
            long itineraryId,
            long itemId,
            ItineraryCommands.PutItemPlace place,
            Instant now
    ) {
        mapper.upsertItemPlace(itineraryId, itemId, place, now);
    }

    @Override
    public int deleteItemPlace(long itineraryId, long itemId) {
        return mapper.deleteItemPlace(itineraryId, itemId);
    }

    @Override
    public Optional<ItineraryModels.DayMapInput> findDayMapInput(long itineraryId, long dayId) {
        ItineraryRows.DayMapRow day = mapper.findDayMap(itineraryId, dayId);
        if (day == null) {
            return Optional.empty();
        }
        List<ItineraryModels.MapItem> items = mapper.findDayMapItems(itineraryId, dayId).stream()
                .map(row -> new ItineraryModels.MapItem(
                        row.itemId(), row.title(), row.startTime(), row.endTime(), row.placeName(),
                        placeReference(
                                row.placeSource(), row.sourcePlaceId(), row.referenceName(),
                                row.referenceAddress(), row.administrativeAreaCode(), row.longitude(),
                                row.latitude(), row.coordinateSystem(), row.confirmedAt(),
                                row.referenceUpdatedAt()
                        ),
                        row.position()
                ))
                .toList();
        return Optional.of(new ItineraryModels.DayMapInput(
                day.itineraryId(), day.dayId(), day.date(), items
        ));
    }

    @Override
    public Optional<Long> findOwnerMemberId(long itineraryId) {
        return Optional.ofNullable(mapper.findOwnerMemberId(itineraryId));
    }

    @Override
    public Optional<ItineraryModels.Snapshot> findSnapshot(long itineraryId) {
        ItineraryRows.ItineraryRow itinerary = mapper.findItinerary(itineraryId);
        return assembleSnapshot(itineraryId, itinerary);
    }

    @Override
    public Optional<ItineraryModels.Snapshot> findSnapshotForUpdate(long itineraryId) {
        ItineraryRows.ItineraryRow itinerary = mapper.findItineraryForUpdate(itineraryId);
        return assembleSnapshot(itineraryId, itinerary);
    }

    @Override
    public int updateOverview(
            long itineraryId,
            String title,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate,
            String timeZone,
            String baseCurrency,
            long expectedVersion,
            long nextVersion,
            Instant now
    ) {
        return mapper.updateOverview(
                itineraryId, title, startDate, endDate, timeZone, baseCurrency,
                expectedVersion, nextVersion, now
        );
    }

    @Override
    public int bumpVersion(long itineraryId, long expectedVersion, long nextVersion, Instant now) {
        return mapper.bumpVersion(itineraryId, expectedVersion, nextVersion, now);
    }

    @Override
    public int updateStatus(
            long itineraryId,
            ItineraryStatus status,
            long expectedVersion,
            long nextVersion,
            Instant now
    ) {
        return mapper.updateStatus(itineraryId, status, expectedVersion, nextVersion, now);
    }

    @Override
    public void deleteEmptyDaysOutside(
            long itineraryId,
            java.time.LocalDate startDate,
            java.time.LocalDate endDate
    ) {
        mapper.deleteEmptyDaysOutside(itineraryId, startDate, endDate);
    }

    @Override
    public void deleteDestinations(long itineraryId) {
        mapper.deleteDestinations(itineraryId);
    }

    private Optional<ItineraryModels.Snapshot> assembleSnapshot(
            long itineraryId,
            ItineraryRows.ItineraryRow itinerary
    ) {
        if (itinerary == null) {
            return Optional.empty();
        }
        List<ItineraryModels.Destination> destinations = mapper.findDestinations(itineraryId).stream()
                .map(row -> new ItineraryModels.Destination(
                        row.id(), row.name(), row.countryCode(), ZoneId.of(row.timeZone()), row.position()
                ))
                .toList();
        Map<Long, List<ItineraryModels.Item>> itemsByDay = mapper.findItems(itineraryId).stream()
                .map(row -> new ItineraryModels.Item(
                        row.id(), row.itineraryDayId(), row.title(), row.placeName(),
                        row.startTime(), row.endTime(), row.notes(), row.estimatedCost(),
                        row.position(), row.deletedAt(), placeReference(
                                row.placeSource(), row.sourcePlaceId(), row.referenceName(),
                                row.referenceAddress(), row.administrativeAreaCode(), row.longitude(),
                                row.latitude(), row.coordinateSystem(), row.confirmedAt(),
                                row.referenceUpdatedAt()
                        )
                ))
                .collect(Collectors.groupingBy(ItineraryModels.Item::dayId));
        List<ItineraryModels.Day> days = mapper.findDays(itineraryId).stream()
                .map(row -> new ItineraryModels.Day(
                        row.id(), row.dayDate(), itemsByDay.getOrDefault(row.id(), List.of())
                ))
                .toList();
        return Optional.of(new ItineraryModels.Snapshot(
                itinerary.id(), itinerary.ownerMemberId(), itinerary.title(),
                itinerary.startDate(), itinerary.endDate(), ZoneId.of(itinerary.timeZone()),
                Currency.getInstance(itinerary.baseCurrency()), ItineraryStatus.valueOf(itinerary.status()),
                itinerary.version(), destinations, days
        ));
    }

    @Override
    public List<ItineraryModels.Summary> findSummaries(
            long ownerMemberId,
            List<ItineraryStatus> statuses,
            Instant cursorUpdatedAt,
            Long cursorId,
            int limit
    ) {
        return mapper.findSummaries(ownerMemberId, statuses, cursorUpdatedAt, cursorId, limit).stream()
                .map(row -> new ItineraryModels.Summary(
                        row.id(), row.title(), row.startDate(), row.endDate(),
                        row.primaryDestination(), ItineraryStatus.valueOf(row.status()),
                        row.version(), row.updatedAt()
                ))
                .toList();
    }

    private static ItineraryModels.PlaceReference placeReference(
            String source,
            String sourcePlaceId,
            String name,
            String address,
            String administrativeAreaCode,
            java.math.BigDecimal longitude,
            java.math.BigDecimal latitude,
            String coordinateSystem,
            Instant confirmedAt,
            Instant updatedAt
    ) {
        if (source == null) {
            return null;
        }
        return new ItineraryModels.PlaceReference(
                ItineraryModels.PlaceSource.valueOf(source), sourcePlaceId, name, address,
                administrativeAreaCode, longitude, latitude, coordinateSystem,
                confirmedAt, updatedAt
        );
    }
}
