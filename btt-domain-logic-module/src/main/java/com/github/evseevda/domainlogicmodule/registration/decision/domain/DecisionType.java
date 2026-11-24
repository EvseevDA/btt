package com.github.evseevda.domainlogicmodule.registration.decision.domain;

public enum DecisionType {
    APPROVE(1),
    REJECT(2);

    private final int id;

    DecisionType(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static DecisionType fromId(int id) {
        for (DecisionType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DecisionType ID: " + id);
    }
}
