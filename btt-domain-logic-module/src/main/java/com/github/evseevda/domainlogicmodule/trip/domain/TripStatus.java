package com.github.evseevda.domainlogicmodule.trip.domain;

public enum TripStatus {
    SCHEDULED(1),
    COMPLETED(2),
    CANCELLED(3);

    private final int id;

    TripStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TripStatus fromId(int id) {
        for (TripStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TripStatus ID: " + id);
    }
}
