package com.github.evseevda.domainlogicmodule.workflow;

/** Содержит имена сообщений, по которым BTT коррелирует события технических BPMN. */
public final class BpmnEventNames {
    /** Сообщение о назначении исполнителя или взятии заявления в работу. */
    public static final String REGISTRATION_EXECUTOR_ASSIGNED =
            "blm_registration_executor_assigned";

    /** Сообщение о передаче решения перевозчика на согласование. */
    public static final String REGISTRATION_DECISION_SUBMITTED =
            "blm_registration_decision_submitted";

    /** Сообщение о возврате решения исполнителю. */
    public static final String REGISTRATION_DECISION_RETURNED =
            "blm_registration_decision_returned";

    /** Сообщение о согласовании текущей редакции решения. */
    public static final String REGISTRATION_DECISION_APPROVED =
            "blm_registration_decision_approved";

    /** Сообщение об отзыве заявления подателем. */
    public static final String REGISTRATION_WITHDRAWAL_REQUESTED =
            "blm_registration_withdrawal_requested";

    /** Сообщение с решением о подключении организации. */
    public static final String ORGANIZATION_REGISTRATION_DECISION_SUBMITTED =
            "blm_organization_registration_decision_submitted";

    /** Сообщение с решением о присоединении сотрудника. */
    public static final String ORGANIZATION_MEMBERSHIP_DECISION_SUBMITTED =
            "blm_organization_membership_decision_submitted";

    private BpmnEventNames() {}
}
