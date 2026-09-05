package com.jiawa.lyw.itinerary.domain;

/** 对外稳定错误类别；不得附带成员、SQL、摘要或私有行程内容。 */
public enum ItineraryError {
    INVALID_ITINERARY,
    INVALID_DESTINATION,
    INVALID_ITEM,
    TIME_CONFLICT,
    DATE_RANGE_CONTAINS_ITEMS,
    ITINERARY_NOT_FOUND,
    VERSION_CONFLICT,
    IDEMPOTENCY_CONFLICT,
    INVALID_STATUS_TRANSITION
}
