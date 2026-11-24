package com.github.evseevda.domainlogicmodule.registration.decision.domain;

public enum DecisionStatus {
    DRAFT(0),
    ISSUED(1);

    private final int id;

    DecisionStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static DecisionStatus fromId(int id) {
        for (DecisionStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DecisionStatus ID: " + id);
    }
}
