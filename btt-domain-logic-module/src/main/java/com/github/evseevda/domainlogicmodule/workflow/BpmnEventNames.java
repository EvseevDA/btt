package com.github.evseevda.domainlogicmodule.workflow;

public final class BpmnEventNames {
    public static final String REGISTRATION_EXECUTOR_ASSIGNED = "blm_registration_executor_assigned";
    public static final String REGISTRATION_DECISION_SUBMITTED = "blm_registration_decision_submitted";
    public static final String REGISTRATION_DECISION_RETURNED = "blm_registration_decision_returned";
    public static final String REGISTRATION_DECISION_APPROVED = "blm_registration_decision_approved";
    public static final String REGISTRATION_WITHDRAWAL_REQUESTED = "blm_registration_withdrawal_requested";

    public static final String ORGANIZATION_REGISTRATION_DECISION_SUBMITTED =
            "blm_organization_registration_decision_submitted";
    public static final String ORGANIZATION_MEMBERSHIP_DECISION_SUBMITTED =
            "blm_organization_membership_decision_submitted";

    private BpmnEventNames() {
    }
}
