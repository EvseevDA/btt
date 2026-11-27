package com.github.evseevda.domainlogicmodule.registration.request.domain;

/** Этапы рассмотрения заявления перевозчика и их коды. */
public enum TransporterRequestStatus {
    DRAFT(0),
    SUBMITTED(1),
    UNDER_REVIEW(2),
    APPROVED(3),
    REJECTED(4),
    WITHDRAWN(5),
    AWAITING_APPROVAL(6),
    REVISION_REQUIRED(7);

    private final int id;

    TransporterRequestStatus(int id) {
        this.id = id;
    }

    /** Возвращает постоянный числовой код значения для записи в базу. */
    public int getId() {
        return id;
    }

    /** Восстанавливает значение по числовому коду. Неизвестный код вызывает исключение. */
    public static TransporterRequestStatus fromId(int id) {
        for (TransporterRequestStatus type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown TransporterRequestStatus ID: " + id);
    }
}
