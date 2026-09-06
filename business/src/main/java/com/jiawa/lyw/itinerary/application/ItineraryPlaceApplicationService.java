package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryModels;

public interface ItineraryPlaceApplicationService {
    ItineraryCommands.CommandResult putPlace(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.PutItemPlace> command
    );

    ItineraryCommands.CommandResult removePlace(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.RemoveItemPlace> command
    );

    ItineraryModels.DayMapInput dayMapInput(long actorMemberId, long itineraryId, long dayId);
}
