package com.github.evseevda.domainlogicmodule.organization.domain;

/** Виды заявок организаций и их коды. */
public enum OrganizationRequestType {
    REGISTRATION(1),
    MEMBERSHIP(2);

    private final int id;

    OrganizationRequestType(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static OrganizationRequestType fromId(int id) {
        for (OrganizationRequestType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown OrganizationRequestType ID: " + id);
    }
}
