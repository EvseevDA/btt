package com.github.evseevda.domainlogicmodule.registration.request.domain;

public enum TransporterRequestStatus {
    DRAFT(0),
    SUBMITTED(1),
    UNDER_REVIEW(2),
    APPROVED(3),
    REJECTED(4),
    WITHDRAWN(5);

    private final int id;

    TransporterRequestStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TransporterRequestStatus fromId(int id) {
        for (TransporterRequestStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterRequestStatus ID: " + id);
    }
}
