package com.jiawa.lyw.itinerarymap.domain;

/** 每日地图模块对外可识别的稳定错误类别，不携带供应商或私有行程内容。 */
public enum ItineraryMapError {
    INVALID_PLACE_QUERY,
    INVALID_ROUTE_MODE,
    INVALID_MAP_REQUEST,
    MAP_PROVIDER_UNAVAILABLE
}
