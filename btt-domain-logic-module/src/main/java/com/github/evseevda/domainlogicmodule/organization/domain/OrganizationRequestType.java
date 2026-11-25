package com.github.evseevda.domainlogicmodule.organization.domain;

public enum OrganizationRequestType {
    REGISTRATION(1), MEMBERSHIP(2);

    private final int id;

    OrganizationRequestType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static OrganizationRequestType fromId(int id) {
        for (OrganizationRequestType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OrganizationRequestType ID: " + id);
    }
}
