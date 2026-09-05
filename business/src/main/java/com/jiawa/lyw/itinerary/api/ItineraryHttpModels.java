package com.jiawa.lyw.itinerary.api;

import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class ItineraryHttpModels {
    private ItineraryHttpModels() {
    }

    public record CommandRequest<T>(UUID commandId, long expectedVersion, T payload) {
        <R> ItineraryCommands.CommandEnvelope<R> envelope(R commandPayload) {
            return new ItineraryCommands.CommandEnvelope<>(commandId, expectedVersion, commandPayload);
        }
    }

    public record DestinationInput(Long id, String name, String countryCode, String timeZone) {
        ItineraryCommands.DestinationInput command() {
            return new ItineraryCommands.DestinationInput(id, name, countryCode, timeZone);
        }
    }

    public record CreatePayload(
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency,
            List<DestinationInput> destinations
    ) {
        ItineraryCommands.CreateItinerary command() {
            return new ItineraryCommands.CreateItinerary(
                    title, startDate, endDate, timeZone, baseCurrency,
                    destinations == null ? null : destinations.stream().map(DestinationInput::command).toList()
            );
        }
    }

    public record UpdateOverviewPayload(
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency
    ) {
        ItineraryCommands.UpdateOverview command() {
            return new ItineraryCommands.UpdateOverview(title, startDate, endDate, timeZone, baseCurrency);
        }
    }

    public record ReplaceDestinationsPayload(List<DestinationInput> destinations) {
        ItineraryCommands.ReplaceDestinations command() {
            return new ItineraryCommands.ReplaceDestinations(
                    destinations == null ? null : destinations.stream().map(DestinationInput::command).toList()
            );
        }
    }

    public record ItemPayload(
            Long dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost
    ) {
        ItineraryCommands.AddItem addCommand() {
            return new ItineraryCommands.AddItem(
                    requiredDayId(), title, placeName, startTime, endTime, notes, estimatedCost
            );
        }

        ItineraryCommands.UpdateItem updateCommand() {
            return new ItineraryCommands.UpdateItem(
                    requiredDayId(), title, placeName, startTime, endTime, notes, estimatedCost
            );
        }

        private long requiredDayId() {
            return dayId == null ? 0 : dayId;
        }
    }

    public record DeletePayload() {
    }

    public record ReorderPayload(List<Long> itemIds) {
    }

    public record TransitionPayload(ItineraryStatus toStatus) {
        ItineraryCommands.TransitionStatus command() {
            return new ItineraryCommands.TransitionStatus(toStatus);
        }
    }

    public record CommandResponse(String itineraryId, String itemId, long version, boolean replayed) {
        static CommandResponse from(ItineraryCommands.CommandResult result) {
            return new CommandResponse(
                    Long.toString(result.itineraryId()),
                    result.itemId() == null ? null : Long.toString(result.itemId()),
                    result.version(),
                    result.replayed()
            );
        }
    }

    public record SummaryResponse(
            String id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String primaryDestination,
            ItineraryStatus status,
            long version,
            String updatedAt
    ) {
        static SummaryResponse from(ItineraryModels.Summary summary) {
            return new SummaryResponse(
                    Long.toString(summary.id()), summary.title(), summary.startDate(), summary.endDate(),
                    summary.primaryDestination(), summary.status(), summary.version(), summary.updatedAt().toString()
            );
        }
    }

    public record PageResponse(List<SummaryResponse> items, String nextCursor) {
        static PageResponse from(ItineraryModels.PageSlice<ItineraryModels.Summary> page) {
            return new PageResponse(page.items().stream().map(SummaryResponse::from).toList(), page.nextCursor());
        }
    }

    public record DestinationResponse(
            String id,
            String name,
            String countryCode,
            String timeZone,
            long position
    ) {
        static DestinationResponse from(ItineraryModels.Destination destination) {
            return new DestinationResponse(
                    Long.toString(destination.id()), destination.name(), destination.countryCode(),
                    destination.timeZone().getId(), destination.position()
            );
        }
    }

    public record ItemResponse(
            String id,
            String dayId,
            String title,
            String placeName,
            LocalTime startTime,
            LocalTime endTime,
            String notes,
            BigDecimal estimatedCost,
            long position
    ) {
        static ItemResponse from(ItineraryModels.Item item) {
            return new ItemResponse(
                    Long.toString(item.id()), Long.toString(item.dayId()), item.title(), item.placeName(),
                    item.startTime(), item.endTime(), item.notes(), item.estimatedCost(), item.position()
            );
        }
    }

    public record DayResponse(String id, LocalDate date, List<ItemResponse> items) {
        static DayResponse from(ItineraryModels.Day day) {
            return new DayResponse(
                    Long.toString(day.id()), day.date(), day.items().stream().map(ItemResponse::from).toList()
            );
        }
    }

    public record SnapshotResponse(
            String id,
            String title,
            LocalDate startDate,
            LocalDate endDate,
            String timeZone,
            String baseCurrency,
            ItineraryStatus status,
            long version,
            List<DestinationResponse> destinations,
            List<DayResponse> days,
            List<ItineraryStatus> allowedTransitions,
            ItineraryStatus suggestedStatus
    ) {
        static SnapshotResponse from(ItineraryModels.Detail detail) {
            ItineraryModels.Snapshot snapshot = detail.snapshot();
            return new SnapshotResponse(
                    Long.toString(snapshot.id()), snapshot.title(), snapshot.startDate(), snapshot.endDate(),
                    snapshot.timeZone().getId(), snapshot.baseCurrency().getCurrencyCode(), snapshot.status(),
                    snapshot.version(),
                    snapshot.destinations().stream().map(DestinationResponse::from).toList(),
                    snapshot.days().stream().map(DayResponse::from).toList(),
                    detail.allowedTransitions(),
                    detail.suggestedStatus()
            );
        }
    }

    public record ErrorContent(String errorCode) {
    }
}
