package com.github.evseevda.domainlogicmodule.trip.domain;

/** Состояния выполнения рейса и их коды. */
public enum TripStatus {
    SCHEDULED(1),
    COMPLETED(2),
    CANCELLED(3);

    private final int id;

    TripStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static TripStatus fromId(int id) {
        for (TripStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TripStatus ID: " + id);
    }
}
