package com.jiawa.lyw.itinerarymap.application;

import com.jiawa.lyw.itinerary.application.ItineraryCommands;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.DailyMapView;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceCandidate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceSearchQuery;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode;

import java.util.List;

/** 行程地点确认、地点搜索和每日地图的公开应用边界。 */
public interface ItineraryMapApplicationService {
    List<PlaceCandidate> search(long actorMemberId, PlaceSearchQuery query);

    ItineraryCommands.CommandResult confirmPlace(
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

    DailyMapView dailyMap(long actorMemberId, long itineraryId, long dayId, RouteMode mode);
}
