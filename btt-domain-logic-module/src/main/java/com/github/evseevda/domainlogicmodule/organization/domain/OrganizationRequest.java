package com.github.evseevda.domainlogicmodule.organization.domain;

import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestTypeConverter;
import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Convert;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "organization_request")
public class OrganizationRequest {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Convert(converter = OrganizationRequestTypeConverter.class)
    @Column(name = "type", nullable = false)
    private OrganizationRequestType type;

    @Convert(converter = OrganizationRequestStatusConverter.class)
    @Column(name = "status", nullable = false)
    private OrganizationRequestStatus status;

    @Column(name = "organization_name", length = 255)
    private String organizationName;

    @Column(name = "inn", length = 12)
    private String inn;

    @Column(name = "ogrn", length = 15)
    private String ogrn;

    @Column(name = "legal_address", columnDefinition = "text")
    private String legalAddress;

    @Column(name = "authority_evidence", columnDefinition = "text")
    private String authorityEvidence;

    @Column(name = "decided_by_id")
    private UUID decidedById;

    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @Column(name = "pending_approval")
    private Boolean pendingApproval;

    protected OrganizationRequest() {
    }
}
