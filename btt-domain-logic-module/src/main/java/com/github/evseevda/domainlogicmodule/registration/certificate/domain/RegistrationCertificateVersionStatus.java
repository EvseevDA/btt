package com.github.evseevda.domainlogicmodule.registration.certificate.domain;

public enum RegistrationCertificateVersionStatus {
    CURRENT(1),
    ARCHIVED(2);

    private final int id;

    RegistrationCertificateVersionStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static RegistrationCertificateVersionStatus fromId(int id) {
        for (RegistrationCertificateVersionStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RegistrationCertificateVersionStatus ID: " + id);
    }
}
