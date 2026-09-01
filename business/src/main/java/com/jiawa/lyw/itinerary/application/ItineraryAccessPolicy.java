package com.jiawa.lyw.itinerary.application;

public interface ItineraryAccessPolicy {
    void assertCanRead(long actorMemberId, long ownerMemberId);

    void assertCanEdit(long actorMemberId, long ownerMemberId);
}
