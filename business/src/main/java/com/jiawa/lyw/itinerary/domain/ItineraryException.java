package com.jiawa.lyw.itinerary.domain;

public final class ItineraryException extends RuntimeException {
    private final ItineraryError error;

    public ItineraryException(ItineraryError error, String safeMessage) {
        super(safeMessage);
        this.error = error;
    }

    public ItineraryError error() {
        return error;
    }
}
