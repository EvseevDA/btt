package com.github.evseevda.domainlogicmodule.registration.certificate.domain;

public enum RegistrationCertificateStatus {
    ACTIVE(1),
    REVOKED(2);

    private final int id;

    RegistrationCertificateStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static RegistrationCertificateStatus fromId(int id) {
        for (RegistrationCertificateStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RegistrationCertificateStatus ID: " + id);
    }
}
