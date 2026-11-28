package com.github.evseevda.domainlogicmodule.registration.certificate.domain;

/** Состояния отдельных версий РУ и их коды. */
public enum RegistrationCertificateVersionStatus {
    CURRENT(1),
    ARCHIVED(2),
    REVOKED(3);

    private final int id;

    RegistrationCertificateVersionStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static RegistrationCertificateVersionStatus fromId(int id) {
        for (RegistrationCertificateVersionStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException(
                "Unknown RegistrationCertificateVersionStatus ID: " + id);
    }
}
