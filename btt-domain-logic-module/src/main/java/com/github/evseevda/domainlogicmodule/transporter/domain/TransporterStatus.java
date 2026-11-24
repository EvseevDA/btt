package com.github.evseevda.domainlogicmodule.transporter.domain;

public enum TransporterStatus {
    UNREGISTERED(0),
    REGISTERED(1),
    REGISTRATION_CANCELLED(2);

    private final int id;

    TransporterStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TransporterStatus fromId(int id) {
        for (TransporterStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterStatus ID: " + id);
    }
}
