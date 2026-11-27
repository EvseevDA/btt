package com.github.evseevda.domainlogicmodule.registration.decision.domain;

/** Состояние согласования решения и его код в базе. */
public enum DecisionStatus {
    DRAFT(0),
    ISSUED(1);

    private final int id;

    DecisionStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static DecisionStatus fromId(int id) {
        for (DecisionStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DecisionStatus ID: " + id);
    }
}
