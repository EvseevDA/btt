package com.github.evseevda.domainlogicmodule.registration.decision.domain;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.conversion.DecisionTypeConverter;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequest;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "decision")
public class Decision {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "request_id", nullable = false, unique = true)
    private TransporterRequest request;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "decided_by_id", nullable = false)
    private UserAccount decidedBy;

    @Convert(converter = DecisionTypeConverter.class)
    @Column(name = "type", nullable = false)
    private DecisionType type;

    @Column(name = "comment", columnDefinition = "text")
    private String comment;

    @Convert(converter = DecisionStatusConverter.class)
    @Column(name = "status", nullable = false)
    private DecisionStatus status;

    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "approved_by_id")
    private UserAccount approvedBy;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "return_reason", columnDefinition = "text")
    private String returnReason;

    @Column(name = "change_description", columnDefinition = "text")
    private String changeDescription;

    @Column(name = "revision", nullable = false)
    private int revision;

    protected Decision() {
    }
}
