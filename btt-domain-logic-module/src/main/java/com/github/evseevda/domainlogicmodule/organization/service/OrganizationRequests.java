package com.github.evseevda.domainlogicmodule.organization.service;

import com.github.evseevda.domainlogic.model.JoinOrganization;
import com.github.evseevda.domainlogic.model.OrganizationRequestDto;
import com.github.evseevda.domainlogic.model.RegisterOrganization;
import com.github.evseevda.domainlogic.model.ReviewOrganizationRequest;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowCommands;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowJobHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@Profile("!mock-api")
public class OrganizationRequests implements WorkflowJobHandler {
    private static final String REVIEW_REGISTRATION = "organization.registration.decide";
    private static final String REGISTRATION_APPROVE = "blm_organization_registration_approve";
    private static final String REGISTRATION_REJECT = "blm_organization_registration_reject";

    private static final String MEMBERSHIP_APPROVE = "blm_organization_membership_approve";
    private static final String MEMBERSHIP_REJECT = "blm_organization_membership_reject";

    private final JdbcClient db;
    private final WorkflowCommands workflow;

    public OrganizationRequests(JdbcClient db, WorkflowCommands workflow) {
        this.db = db;
        this.workflow = workflow;
    }

    @Transactional
    public OrganizationRequestDto register(RegisterOrganization form) {
        UUID applicant = CurrentAccount.id();
        UUID request = UUID.randomUUID();
        if (form.getName().isBlank() || form.getLegalAddress().isBlank() || form.getAuthorityEvidence().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заполните реквизиты и подтверждение полномочий");
        }
        boolean alreadyRegistered = db.sql("""
                SELECT EXISTS (SELECT 1 FROM transporter_organization WHERE inn = :inn OR ogrn = :ogrn)
                """).param("inn", form.getInn()).param("ogrn", form.getOgrn()).query(Boolean.class).single();
        if (alreadyRegistered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Организация уже подключена");
        }
        try {
            db.sql("""
                    INSERT INTO organization_request (id, applicant_id, type, status, organization_name, inn, ogrn,
                        legal_address, authority_evidence, created_at)
                    VALUES (:id, :applicant, 1, 1, :name, :inn, :ogrn, :address, :evidence,
                        timezone('UTC', clock_timestamp()))
                    """).param("id", request).param("applicant", applicant).param("name", form.getName().strip())
                    .param("inn", form.getInn()).param("ogrn", form.getOgrn()).param("address", form.getLegalAddress().strip())
                    .param("evidence", form.getAuthorityEvidence().strip()).update();
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка организации уже рассматривается", ex);
        }
        workflow.start(request, "blm_organization_registration");
        return get(request);
    }

    @Transactional
    public OrganizationRequestDto join(JoinOrganization form) {
        UUID applicant = CurrentAccount.id();
        UUID organization = form.getOrganizationId();
        boolean exists = db.sql("SELECT EXISTS (SELECT 1 FROM transporter_organization WHERE id = :id)")
                .param("id", organization).query(Boolean.class).single();
        if (!exists) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        boolean member = db.sql("""
                SELECT EXISTS (SELECT 1 FROM transporter_organization_users WHERE organization_id = :org AND user_id = :user)
                """).param("org", organization).param("user", applicant).query(Boolean.class).single();
        if (member) throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь уже состоит в организации");
        UUID request = UUID.randomUUID();
        try {
            db.sql("""
                    INSERT INTO organization_request (id, applicant_id, organization_id, type, status, created_at)
                    VALUES (:id, :user, :org, 2, 1, timezone('UTC', clock_timestamp()))
                    """).param("id", request).param("user", applicant).param("org", organization).update();
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассматривается", ex);
        }
        workflow.start(request, "blm_organization_membership");
        return get(request);
    }

    @Transactional
    public void updatePermissions(UUID organization, UUID user, boolean represent, boolean confirm) {
        UUID actor = CurrentAccount.id();
        if (actor.equals(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Собственные полномочия меняет другой представитель");
        }
        if (!mayConfirm(organization, actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        int updated = db.sql("""
                UPDATE transporter_organization_users
                SET can_represent = :represent, can_confirm_members = :confirm
                WHERE organization_id = :org AND user_id = :user
                """).param("org", organization).param("user", user).param("represent", represent)
                .param("confirm", confirm).update();
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    public OrganizationRequestDto get(UUID id) {
        var request = find(id, false);
        if (!request.getApplicantId().equals(CurrentAccount.id())) {
            requireReviewer(request, CurrentAccount.id());
        }
        return request;
    }

    @Transactional
    public OrganizationRequestDto decide(UUID id, ReviewOrganizationRequest review) {
        UUID reviewer = CurrentAccount.id();
        var request = find(id, true);
        requireReviewer(request, reviewer);
        if (request.getApplicantId().equals(reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нельзя подтвердить собственную заявку");
        }
        if (request.getStatus() != 1 || request.getDecidedById() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Решение уже принято");
        }
        if (!review.getApproved() && (review.getComment() == null || review.getComment().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите причину отказа");
        }
        db.sql("""
                UPDATE organization_request SET decided_by_id = :reviewer, decision_comment = :comment,
                    pending_approval = :approval WHERE id = :id
                """).param("id", id).param("reviewer", reviewer).param("comment", review.getComment())
                .param("approval", review.getApproved()).update();
        String event = request.getType() == 1 ? BpmnEventNames.ORGANIZATION_REGISTRATION_DECISION_SUBMITTED
                : BpmnEventNames.ORGANIZATION_MEMBERSHIP_DECISION_SUBMITTED;
        workflow.message(id, event, Map.of("approved", review.getApproved()));
        return find(id, false);
    }

    @Override
    public Set<String> jobTypes() {
        return Set.of(REGISTRATION_APPROVE, REGISTRATION_REJECT, MEMBERSHIP_APPROVE, MEMBERSHIP_REJECT);
    }

    @Override
    @Transactional
    public Map<String, ?> handle(String type, JsonNode variables) {
        UUID id = UUID.fromString(variables.path("requestId").asText());
        var request = find(id, true);
        if (request.getStatus() != 1) return Map.of();
        boolean registration = REGISTRATION_APPROVE.equals(type) || REGISTRATION_REJECT.equals(type);
        boolean approved = REGISTRATION_APPROVE.equals(type) || MEMBERSHIP_APPROVE.equals(type);
        var pending = db.sql("SELECT pending_approval FROM organization_request WHERE id = :id")
                .param("id", id).query(Boolean.class).optional();
        if (request.getDecidedById() == null || registration != (request.getType() == 1)
                || pending.isEmpty() || pending.orElseThrow() != approved) {
            throw new IllegalStateException("No matching organization decision for " + id);
        }
        UUID organization = request.getOrganizationId();
        if (approved) {
            if (registration) organization = connect(request);
            else addMember(organization, request.getApplicantId(), false);
        }
        db.sql("""
                UPDATE organization_request SET status = :status, organization_id = :organization,
                    decided_at = timezone('UTC', clock_timestamp()) WHERE id = :id
                """).param("status", approved ? 2 : 3).param("organization", organization).param("id", id).update();
        return Map.of();
    }

    private UUID connect(OrganizationRequestDto request) {
        var form = request.getOrganizationData();
        UUID organization = UUID.randomUUID();
        db.sql("""
                INSERT INTO transporter_organization (id, name, inn, ogrn, legal_address, created_at)
                VALUES (:id, :name, :inn, :ogrn, :address, timezone('UTC', clock_timestamp()))
                """).param("id", organization).param("name", form.getName()).param("inn", form.getInn())
                .param("ogrn", form.getOgrn()).param("address", form.getLegalAddress()).update();
        addMember(organization, request.getApplicantId(), true);
        return organization;
    }

    private void addMember(UUID organization, UUID user, boolean firstRepresentative) {
        db.sql("""
                INSERT INTO transporter_organization_users (organization_id, user_id, can_represent, can_confirm_members)
                VALUES (:organization, :user, :representative, :representative)
                ON CONFLICT (organization_id, user_id) DO NOTHING
                """).param("organization", organization).param("user", user)
                .param("representative", firstRepresentative).update();
    }

    private void requireReviewer(OrganizationRequestDto request, UUID reviewer) {
        if (request.getType() == 1) {
            CurrentAccount.require(REVIEW_REGISTRATION);
        } else if (!mayConfirm(request.getOrganizationId(), reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private boolean mayConfirm(UUID organization, UUID user) {
        return db.sql("""
                SELECT EXISTS (SELECT 1 FROM transporter_organization_users
                    WHERE organization_id = :org AND user_id = :user AND can_confirm_members)
                """).param("org", organization).param("user", user).query(Boolean.class).single();
    }

    private OrganizationRequestDto find(UUID id, boolean lock) {
        return db.sql("SELECT * FROM organization_request WHERE id = :id" + (lock ? " FOR UPDATE" : ""))
                .param("id", id).query((rs, row) -> {
                    int type = rs.getInt("type");
                    return new OrganizationRequestDto(id, rs.getObject("applicant_id", UUID.class), type,
                            rs.getInt("status"), rs.getObject("created_at", LocalDateTime.class).toString())
                            .organizationId(rs.getObject("organization_id", UUID.class))
                            .decidedById(rs.getObject("decided_by_id", UUID.class))
                            .decisionComment(rs.getString("decision_comment"))
                            .organizationData(type == 1 ? new RegisterOrganization(rs.getString("organization_name"),
                                    rs.getString("inn"), rs.getString("ogrn"), rs.getString("legal_address"),
                                    rs.getString("authority_evidence")) : null);
                }).optional().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
