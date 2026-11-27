package com.github.evseevda.domainlogicmodule.workflow;

/** Имена типов заданий из технических BPMN. */
public final class BpmnJobTypes {
    /** Тип задания Zeebe. Подключает организацию и назначает первого представителя. */
    public static final String ORGANIZATION_REGISTRATION_APPROVE = "blm_organization_registration_approve";

    /** Тип задания Zeebe. Сохраняет отказ в подключении организации. */
    public static final String ORGANIZATION_REGISTRATION_REJECT = "blm_organization_registration_reject";

    /** Тип задания Zeebe. Подтверждает присоединение сотрудника к организации. */
    public static final String ORGANIZATION_MEMBERSHIP_APPROVE = "blm_organization_membership_approve";

    /** Тип задания Zeebe. Сохраняет отказ в присоединении сотрудника. */
    public static final String ORGANIZATION_MEMBERSHIP_REJECT = "blm_organization_membership_reject";

    /** Тип задания Zeebe. Сохраняет назначение исполнителя. */
    public static final String REGISTRATION_ASSIGNMENT = "blm_save_registration_assignment";

    /** Тип задания Zeebe. Передаёт сохранённую редакцию решения на согласование. */
    public static final String REGISTRATION_DECISION = "blm_save_registration_decision";

    /** Тип задания Zeebe. Возвращает решение исполнителю. */
    public static final String REGISTRATION_RETURN = "blm_return_registration_decision";

    /** Тип задания Zeebe. Применяет отзыв заявления. */
    public static final String REGISTRATION_WITHDRAW = "blm_withdraw_registration_request";

    /** Тип задания Zeebe. Применяет согласованный отказ. */
    public static final String REGISTRATION_REFUSAL = "blm_finalize_registration_refusal";

    /** Тип задания Zeebe. При положительном решении создаёт перевозчика и первое РУ. */
    public static final String INITIAL_REGISTRATION = "blm_apply_initial_registration";

    /** Тип задания Zeebe. Сохраняет новые версии перевозчика и РУ. */
    public static final String REGISTRATION_AMENDMENT = "blm_apply_registration_amendment";

    /** Тип задания Zeebe. Фиксирует отмену регистрации по инициативе перевозчика. */
    public static final String CARRIER_CANCELLATION = "blm_apply_carrier_cancellation";

    /** Тип задания Zeebe. Фиксирует отмену регистрации по инициативе администрации. */
    public static final String ADMIN_CANCELLATION = "blm_apply_admin_cancellation";

    private BpmnJobTypes() {
    }
}
