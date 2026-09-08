package com.jiawa.lyw.itinerarymap.domain;

public final class ItineraryMapException extends RuntimeException {
    private final ItineraryMapError error;

    public ItineraryMapException(ItineraryMapError error, String safeMessage) {
        super(safeMessage);
        this.error = error;
    }

    public ItineraryMapError error() {
        return error;
    }
}
