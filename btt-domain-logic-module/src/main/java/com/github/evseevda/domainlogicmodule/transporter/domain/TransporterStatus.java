package com.github.evseevda.domainlogicmodule.transporter.domain;

/** Состояния регистрации перевозчика и их коды. */
public enum TransporterStatus {
    UNREGISTERED(0),
    REGISTERED(1),
    REGISTRATION_CANCELLED(2);

    private final int id;

    TransporterStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static TransporterStatus fromId(int id) {
        for (TransporterStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterStatus ID: " + id);
    }
}
