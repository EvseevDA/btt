package com.github.evseevda.domainlogicmodule.organization.domain;

/** Состояния проверки заявки организации и их коды. */
public enum OrganizationRequestStatus {
    PENDING(1), APPROVED(2), REJECTED(3);

    private final int id;

    OrganizationRequestStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static OrganizationRequestStatus fromId(int id) {
        for (OrganizationRequestStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OrganizationRequestStatus ID: " + id);
    }
}
