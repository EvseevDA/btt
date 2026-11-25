package com.github.evseevda.domainlogicmodule.organization.service;

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

    public OrganizationRequestDto get(UUID id) {
        var request = find(id, false);
        if (!request.getApplicantId().equals(CurrentAccount.id())) {
            CurrentAccount.require(REVIEW_REGISTRATION);
        }
        return request;
    }

    @Transactional
    public OrganizationRequestDto decide(UUID id, ReviewOrganizationRequest review) {
        UUID reviewer = CurrentAccount.id();
        var request = find(id, true);
        CurrentAccount.require(REVIEW_REGISTRATION);
        if (request.getStatus() != 1 || request.getDecidedById() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Решение уже принято");
        }
        if (!review.getApproved() && (review.getComment() == null || review.getComment().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите причину отказа");
        }
        db.sql("UPDATE organization_request SET decided_by_id = :reviewer, decision_comment = :comment WHERE id = :id")
                .param("id", id).param("reviewer", reviewer).param("comment", review.getComment()).update();
        workflow.message(id, BpmnEventNames.ORGANIZATION_REGISTRATION_DECISION_SUBMITTED,
                Map.of("approved", review.getApproved()));
        return find(id, false);
    }

    @Override
    public Set<String> jobTypes() {
        return Set.of(REGISTRATION_APPROVE, REGISTRATION_REJECT);
    }

    @Override
    @Transactional
    public Map<String, ?> handle(String type, JsonNode variables) {
        UUID id = UUID.fromString(variables.path("requestId").asText());
        var request = find(id, true);
        if (request.getStatus() != 1) return Map.of();
        if (request.getDecidedById() == null || request.getType() != 1) {
            throw new IllegalStateException("No authorized registration decision for " + id);
        }
        boolean approved = REGISTRATION_APPROVE.equals(type);
        UUID organization = approved ? connect(request) : null;
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
        db.sql("""
                INSERT INTO transporter_organization_users (organization_id, user_id, can_represent, can_confirm_members)
                VALUES (:organization, :user, true, true)
                """).param("organization", organization).param("user", request.getApplicantId()).update();
        return organization;
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
