package com.jiawa.lyw.itinerary.application;

import com.jiawa.lyw.itinerary.domain.ItineraryError;
import com.jiawa.lyw.itinerary.domain.ItineraryException;

public final class OwnerOnlyItineraryAccessPolicy implements ItineraryAccessPolicy {
    @Override
    public void assertCanRead(long actorMemberId, long ownerMemberId) {
        assertOwner(actorMemberId, ownerMemberId);
    }

    @Override
    public void assertCanEdit(long actorMemberId, long ownerMemberId) {
        assertOwner(actorMemberId, ownerMemberId);
    }

    private static void assertOwner(long actorMemberId, long ownerMemberId) {
        if (actorMemberId <= 0 || actorMemberId != ownerMemberId) {
            throw new ItineraryException(ItineraryError.ITINERARY_NOT_FOUND, "行程不存在");
        }
    }
}
