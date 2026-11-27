package com.github.evseevda.domainlogicmodule.registration.request.domain;

/** Виды процессов регистрации перевозчика и их коды. */
public enum TransporterRequestType {
    INITIAL_REGISTRATION(1),
    CERTIFICATE_AMENDMENT(2),
    CANCELLATION_BY_TRANSPORTER(3),
    CANCELLATION_BY_PORTAL_ADMINISTRATION(4);

    private final int id;

    TransporterRequestType(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static TransporterRequestType fromId(int id) {
        for (TransporterRequestType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterRequestType ID: " + id);
    }
}
