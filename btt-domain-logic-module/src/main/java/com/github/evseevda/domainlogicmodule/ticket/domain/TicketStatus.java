package com.github.evseevda.domainlogicmodule.ticket.domain;

/** Состояния бронирования билета и их коды. */
public enum TicketStatus {
    BOOKED(1),
    CANCELLED(2);

    private final int id;

    TicketStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static TicketStatus fromId(int id) {
        for (TicketStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TicketStatus ID: " + id);
    }
}
