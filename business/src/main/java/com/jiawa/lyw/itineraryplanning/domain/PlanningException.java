package com.jiawa.lyw.itineraryplanning.domain;

public final class PlanningException extends RuntimeException {
    private final PlanningError error;

    public PlanningException(PlanningError error, String message) {
        super(message);
        this.error = error;
    }

    public PlanningError error() {
        return error;
    }
}
