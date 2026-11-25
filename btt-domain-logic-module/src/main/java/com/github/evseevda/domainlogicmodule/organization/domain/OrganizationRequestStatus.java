package com.github.evseevda.domainlogicmodule.organization.domain;

public enum OrganizationRequestStatus {
    PENDING(1), APPROVED(2), REJECTED(3);

    private final int id;

    OrganizationRequestStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static OrganizationRequestStatus fromId(int id) {
        for (OrganizationRequestStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OrganizationRequestStatus ID: " + id);
    }
}
