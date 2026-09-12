package com.jiawa.lyw.itinerarymap.application;

import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.Coordinate;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteMode;
import com.jiawa.lyw.itinerarymap.domain.ItineraryMapModels.RouteValue;

/** 供应商无关的单段路线规划端口。 */
public interface RoutePlanningGateway {
    RouteValue plan(Coordinate origin, Coordinate destination, RouteMode mode, String transitCityCode);
}
