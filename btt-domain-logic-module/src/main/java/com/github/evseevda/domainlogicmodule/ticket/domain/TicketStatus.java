package com.github.evseevda.domainlogicmodule.ticket.domain;

public enum TicketStatus {
    BOOKED(1),
    CANCELLED(2);

    private final int id;

    TicketStatus(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public static TicketStatus fromId(int id) {
        for (TicketStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TicketStatus ID: " + id);
    }
}
