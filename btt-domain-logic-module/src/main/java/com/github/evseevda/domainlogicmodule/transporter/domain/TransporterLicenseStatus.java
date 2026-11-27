package com.github.evseevda.domainlogicmodule.transporter.domain;

/** Состояния лицензии перевозчика и их коды. */
public enum TransporterLicenseStatus {
    ACTIVE(1),
    EXPIRED(2),
    REVOKED(3);

    private final int id;

    TransporterLicenseStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static TransporterLicenseStatus fromId(int id) {
        for (TransporterLicenseStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterLicenseStatus ID: " + id);
    }
}
