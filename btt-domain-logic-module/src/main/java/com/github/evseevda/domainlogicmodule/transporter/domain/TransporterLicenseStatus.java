package com.github.evseevda.domainlogicmodule.transporter.domain;

public enum TransporterLicenseStatus {
    ACTIVE(1),
    EXPIRED(2),
    REVOKED(3);

    private final int id;

    TransporterLicenseStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TransporterLicenseStatus fromId(int id) {
        for (TransporterLicenseStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterLicenseStatus ID: " + id);
    }
}
