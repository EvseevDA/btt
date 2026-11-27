package com.github.evseevda.domainlogicmodule.registration.decision.domain;

/** Варианты решения по заявлению перевозчика и их коды. */
public enum DecisionType {
    APPROVE(1),
    REJECT(2);

    private final int id;

    DecisionType(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static DecisionType fromId(int id) {
        for (DecisionType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown DecisionType ID: " + id);
    }
}
