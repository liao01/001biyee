package com.jiawa.lyw.itinerarymap.api;

import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;
import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

/** 每日地图 HTTP 边界：所有聚合、日程和安排标识均序列化为字符串。 */
public final class ItineraryMapHttpModels {
    private ItineraryMapHttpModels() {
    }

    public record PutPlaceRequest(UUID commandId, long expectedVersion, PlaceInput place) {
        ItineraryCommands.CommandEnvelope<ItineraryCommands.PutItemPlace> command() {
            return new ItineraryCommands.CommandEnvelope<>(
                    commandId, expectedVersion, place == null ? null : place.command()
            );
        }
    }

    public record RemovePlaceRequest(UUID commandId, long expectedVersion) {
        ItineraryCommands.CommandEnvelope<ItineraryCommands.RemoveItemPlace> command() {
            return new ItineraryCommands.CommandEnvelope<>(
                    commandId, expectedVersion, new ItineraryCommands.RemoveItemPlace()
            );
        }
    }

    public record PlaceInput(
            String source,
            String sourcePlaceId,
            String name,
            String address,
            String administrativeAreaCode,
            BigDecimal longitude,
            BigDecimal latitude,
            String coordinateSystem
    ) {
        ItineraryCommands.PutItemPlace command() {
            return new ItineraryCommands.PutItemPlace(
                    source(source), sourcePlaceId, name, address, administrativeAreaCode,
                    longitude, latitude, coordinateSystem
            );
        }

        private static ItineraryModels.PlaceSource source(String value) {
            try {
                return ItineraryModels.PlaceSource.valueOf(value == null ? "" : value.strip());
            } catch (IllegalArgumentException exception) {
                throw new ItineraryException(ItineraryError.INVALID_PLACE, "地点信息无效");
            }
        }
    }

    public record PlaceSearchResponse(List<PlaceCandidateResponse> items) {
        static PlaceSearchResponse from(List<ItineraryMapModels.PlaceCandidate> candidates) {
            return new PlaceSearchResponse(candidates.stream().map(PlaceCandidateResponse::from).toList());
        }
    }

    public record PlaceCandidateResponse(
            ItineraryMapModels.PlaceSource source,
            String sourcePlaceId,
            String name,
            String address,
            String administrativeAreaCode,
            BigDecimal longitude,
            BigDecimal latitude,
            String coordinateSystem,
            String administrativeArea
    ) {
        static PlaceCandidateResponse from(ItineraryMapModels.PlaceCandidate candidate) {
            return new PlaceCandidateResponse(
                    candidate.source(), candidate.sourcePlaceId(), candidate.name(), candidate.address(),
                    candidate.administrativeAreaCode(), candidate.coordinate().longitude(), candidate.coordinate().latitude(),
                    candidate.coordinateSystem(), candidate.administrativeArea()
            );
        }
    }

    public record CommandResponse(String itineraryId, String itemId, long version, boolean replayed) {
        static CommandResponse from(ItineraryCommands.CommandResult result) {
            return new CommandResponse(
                    Long.toString(result.itineraryId()),
                    result.itemId() == null ? null : Long.toString(result.itemId()),
                    result.version(), result.replayed()
            );
        }
    }

    public record DailyMapResponse(
            String itineraryId,
            String dayId,
            LocalDate date,
            ItineraryMapModels.RouteMode mode,
            List<StopResponse> stops,
            List<LegResponse> legs,
            boolean degraded,
            String generatedAt
    ) {
        static DailyMapResponse from(ItineraryMapModels.DailyMapView view) {
            return new DailyMapResponse(
                    Long.toString(view.itineraryId()), Long.toString(view.dayId()), view.date(), view.mode(),
                    view.stops().stream().map(StopResponse::from).toList(),
                    view.legs().stream().map(LegResponse::from).toList(),
                    view.degraded(), timestamp(view.generatedAt())
            );
        }
    }

    public record StopResponse(
            int sequence,
            String itemId,
            String title,
            LocalTime startTime,
            LocalTime endTime,
            String placeName,
            PlaceReferenceResponse placeReference,
            ItineraryMapModels.StopStatus status
    ) {
        static StopResponse from(ItineraryMapModels.Stop stop) {
            return new StopResponse(
                    stop.sequence(), Long.toString(stop.itemId()), stop.title(), stop.startTime(), stop.endTime(),
                    stop.placeName(), PlaceReferenceResponse.from(stop.placeReference()), stop.status()
            );
        }
    }

    public record PlaceReferenceResponse(
            ItineraryMapModels.PlaceSource source,
            String sourcePlaceId,
            String name,
            String address,
            String administrativeAreaCode,
            BigDecimal longitude,
            BigDecimal latitude,
            String coordinateSystem,
            String confirmedAt,
            String updatedAt
    ) {
        static PlaceReferenceResponse from(ItineraryMapModels.PlaceReference place) {
            if (place == null) {
                return null;
            }
            return new PlaceReferenceResponse(
                    place.source(), place.sourcePlaceId(), place.name(), place.address(),
                    place.administrativeAreaCode(), place.coordinate().longitude(), place.coordinate().latitude(),
                    place.coordinateSystem(), timestamp(place.confirmedAt()), timestamp(place.updatedAt())
            );
        }
    }

    public record LegResponse(
            String fromItemId,
            String toItemId,
            ItineraryMapModels.RouteMode mode,
            ItineraryMapModels.RouteStatus status,
            Long distanceMeters,
            Long durationSeconds,
            List<CoordinateResponse> polyline,
            String calculatedAt,
            boolean stale,
            String unavailableReason
    ) {
        static LegResponse from(ItineraryMapModels.Leg leg) {
            return new LegResponse(
                    Long.toString(leg.fromItemId()), Long.toString(leg.toItemId()), leg.mode(), leg.status(),
                    leg.distanceMeters(), leg.durationSeconds(),
                    leg.polyline().stream().map(CoordinateResponse::from).toList(), timestamp(leg.calculatedAt()),
                    leg.stale(), leg.unavailableReason()
            );
        }
    }

    public record CoordinateResponse(BigDecimal longitude, BigDecimal latitude) {
        static CoordinateResponse from(ItineraryMapModels.Coordinate coordinate) {
            return new CoordinateResponse(coordinate.longitude(), coordinate.latitude());
        }
    }

    public record ErrorContent(String errorCode) {
    }

    private static String timestamp(Instant value) {
        return value == null ? null : value.toString();
    }
}
