package com.github.evseevda.domainlogicmodule.registration.request.domain;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganization;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersion;
import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestTypeConverter;
import com.github.evseevda.domainlogicmodule.transporter.domain.Transporter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "request")
public class TransporterRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transporter_id")
    private Transporter transporter;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_contact_id", nullable = false)
    private UserAccount applicantContact;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executor_id", nullable = true)
    private UserAccount executor;

    @Convert(converter = TransporterRequestTypeConverter.class)
    @Column(name = "type", nullable = false)
    private TransporterRequestType type;

    @Convert(converter = TransporterRequestStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TransporterRequestStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private TransporterOrganization organization;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transporter_version_id")
    private TransporterVersion sourceTransporterVersion;

    @Column(name = "proposed_data", columnDefinition = "text")
    private String proposedData;

    @Column(name = "pending_action", length = 100)
    private String pendingAction;

    @Column(name = "pending_command_id")
    private UUID pendingCommandId;

    protected TransporterRequest() {
    }
}
