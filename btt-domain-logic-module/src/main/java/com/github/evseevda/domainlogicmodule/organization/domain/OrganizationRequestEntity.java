package com.github.evseevda.domainlogicmodule.organization.domain;

import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestStatusConverter;
import com.github.evseevda.domainlogicmodule.organization.conversion.OrganizationRequestTypeConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

/** Заявка на подключение организации или присоединение пользователя. */
@Entity
@Getter
@Table(name = "organization_request")
public class OrganizationRequestEntity {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Идентификатор подателя заявки. */
    @Column(name = "applicant_id", nullable = false)
    private UUID applicantId;

    /** Идентификатор организации. */
    @Column(name = "organization_id")
    private UUID organizationId;

    /** Вид заявления или решения. */
    @Convert(converter = OrganizationRequestTypeConverter.class)
    @Column(name = "type", nullable = false)
    private OrganizationRequestType type;

    /** Текущее состояние записи. */
    @Convert(converter = OrganizationRequestStatusConverter.class)
    @Column(name = "status", nullable = false)
    private OrganizationRequestStatus status;

    /** Название юридического лица из заявки. */
    @Column(name = "organization_name", length = 255)
    private String organizationName;

    /** ИНН организации. */
    @Column(name = "inn", length = 12)
    private String inn;

    /** ОГРН организации. */
    @Column(name = "ogrn", length = 15)
    private String ogrn;

    /** Юридический адрес организации. */
    @Column(name = "legal_address", columnDefinition = "text")
    private String legalAddress;

    /** Сведения о полномочиях заявителя. */
    @Column(name = "authority_evidence", columnDefinition = "text")
    private String authorityEvidence;

    /** Идентификатор сотрудника, принявшего решение. */
    @Column(name = "decided_by_id")
    private UUID decidedById;

    /** Комментарий проверяющего. */
    @Column(name = "decision_comment", columnDefinition = "text")
    private String decisionComment;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Время итогового решения в UTC. */
    @Column(name = "decided_at")
    private LocalDateTime decidedAt;

    /** Сохранённый вердикт, ожидающий применения процессом. */
    @Column(name = "pending_approval")
    private Boolean pendingApproval;

    protected OrganizationRequestEntity() {}

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public OrganizationRequestEntity(
            UUID id,
            UUID applicantId,
            UUID organizationId,
            OrganizationRequestType type,
            String organizationName,
            String inn,
            String ogrn,
            String legalAddress,
            String authorityEvidence,
            LocalDateTime createdAt) {
        this.id = id;
        this.applicantId = applicantId;
        this.organizationId = organizationId;
        this.type = type;
        this.organizationName = organizationName;
        this.inn = inn;
        this.ogrn = ogrn;
        this.legalAddress = legalAddress;
        this.authorityEvidence = authorityEvidence;
        this.createdAt = createdAt;
        status = OrganizationRequestStatus.PENDING;
    }

    /** Сохраняет принятое проверяющим решение. */
    public void recordDecision(UUID reviewer, String comment, boolean approval) {
        decidedById = reviewer;
        decisionComment = comment;
        pendingApproval = approval;
    }

    /** Фиксирует результат исполнения процесса. */
    public void complete(OrganizationRequestStatus state, UUID organization, LocalDateTime time) {
        status = state;
        organizationId = organization;
        decidedAt = time;
    }
}
