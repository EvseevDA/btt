package com.github.evseevda.domainlogicmodule.registration.request.domain;

public enum TransporterRequestType {
    INITIAL_REGISTRATION(1),
    CERTIFICATE_AMENDMENT(2),
    CANCELLATION_BY_TRANSPORTER(3),
    CANCELLATION_BY_PORTAL_ADMINISTRATION(4);

    private final int id;

    TransporterRequestType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TransporterRequestType fromId(int id) {
        for (TransporterRequestType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterRequestType ID: " + id);
    }
}
