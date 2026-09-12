package com.jiawa.lyw.itinerarymap.application;

import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceCandidate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.PlaceSearchQuery;

import java.util.List;

/** 供应商无关的地点关键词搜索端口。 */
public interface PlaceSearchGateway {
    List<PlaceCandidate> search(PlaceSearchQuery query);
}
