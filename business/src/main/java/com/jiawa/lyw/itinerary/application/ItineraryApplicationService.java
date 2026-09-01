package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryModels;
import com.jiawa.lyw.itinerary.domain.ItineraryStatus;

import java.util.Set;

public interface ItineraryApplicationService {
    ItineraryModels.PageSlice<ItineraryModels.Summary> list(
            long actorMemberId,
            Set<ItineraryStatus> statuses,
            String cursor,
            int limit
    );

    ItineraryModels.Snapshot get(long actorMemberId, long itineraryId);

    ItineraryCommands.CommandResult create(
            long actorMemberId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.CreateItinerary> command
    );

    ItineraryCommands.CommandResult updateOverview(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.UpdateOverview> command
    );

    ItineraryCommands.CommandResult replaceDestinations(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.ReplaceDestinations> command
    );

    ItineraryCommands.CommandResult addItem(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.AddItem> command
    );

    ItineraryCommands.CommandResult updateItem(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.UpdateItem> command
    );

    ItineraryCommands.CommandResult deleteItem(
            long actorMemberId,
            long itineraryId,
            long itemId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.DeleteItem> command
    );

    ItineraryCommands.CommandResult reorderItems(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.ReorderItems> command
    );

    ItineraryCommands.CommandResult transition(
            long actorMemberId,
            long itineraryId,
            ItineraryCommands.CommandEnvelope<ItineraryCommands.TransitionStatus> command
    );
}
