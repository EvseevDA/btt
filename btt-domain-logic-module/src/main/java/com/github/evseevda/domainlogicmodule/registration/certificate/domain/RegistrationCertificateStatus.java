package com.github.evseevda.domainlogicmodule.registration.certificate.domain;

/** Состояния регистрационного удостоверения и их коды. */
public enum RegistrationCertificateStatus {
    ACTIVE(1),
    REVOKED(2);

    private final int id;

    RegistrationCertificateStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static RegistrationCertificateStatus fromId(int id) {
        for (RegistrationCertificateStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown RegistrationCertificateStatus ID: " + id);
    }
}
