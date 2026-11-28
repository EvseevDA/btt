package com.github.evseevda.domainlogicmodule.registration.request.domain;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccountEntity;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestStatusConverter;
import com.github.evseevda.domainlogicmodule.registration.request.conversion.TransporterRequestTypeConverter;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganizationEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

/** Заявление по регистрации перевозчика и состояние его рассмотрения. */
@Entity
@Getter
@Table(name = "request")
public class TransporterRequestEntity {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Перевозчик, к которому относится запись. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "transporter_id")
    private TransporterEntity transporter;

    /** Пользователь, подавший заявление. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_contact_id", nullable = false)
    private UserAccountEntity applicantContact;

    /** Исполнитель, которому назначено заявление. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "executor_id", nullable = true)
    private UserAccountEntity executor;

    /** Вид заявления или решения. */
    @Convert(converter = TransporterRequestTypeConverter.class)
    @Column(name = "type", nullable = false)
    private TransporterRequestType type;

    /** Текущее состояние записи. */
    @Convert(converter = TransporterRequestStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TransporterRequestStatus status;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Время подачи в UTC. */
    @Column(name = "submitted_at")
    private LocalDateTime submittedAt;

    /** Организация перевозчика. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private TransporterOrganizationEntity organization;

    /** Версия перевозчика, на основе которой подано заявление. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_transporter_version_id")
    private TransporterVersionEntity sourceTransporterVersion;

    /** Сериализованный снимок данных, предложенных в заявлении. */
    @Column(name = "proposed_data", columnDefinition = "text")
    private String proposedData;

    /** Переход, ожидающий обработки в Zeebe. */
    @Column(name = "pending_action", length = 100)
    private String pendingAction;

    /** Идентификатор ожидающей обработки команды. */
    @Column(name = "pending_command_id")
    private UUID pendingCommandId;

    protected TransporterRequestEntity() {}

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public TransporterRequestEntity(
            UUID id,
            TransporterEntity transporter,
            TransporterOrganizationEntity organization,
            TransporterVersionEntity sourceTransporterVersion,
            String proposedData,
            UserAccountEntity applicantContact,
            TransporterRequestType type,
            LocalDateTime createdAt) {
        this.id = id;
        this.transporter = transporter;
        this.organization = organization;
        this.sourceTransporterVersion = sourceTransporterVersion;
        this.proposedData = proposedData;
        this.applicantContact = applicantContact;
        this.type = type;
        this.createdAt = createdAt;
        status = TransporterRequestStatus.DRAFT;
    }

    /** Фиксирует подачу заявления. */
    public void submit(LocalDateTime time) {
        status = TransporterRequestStatus.SUBMITTED;
        submittedAt = time;
    }

    /** Назначает исполнителя и переводит заявление на рассмотрение. */
    public void assign(UserAccountEntity user) {
        executor = user;
        status = TransporterRequestStatus.UNDER_REVIEW;
    }

    /** Сохраняет результат перехода процесса. */
    public void changeStatus(TransporterRequestStatus state) {
        status = state;
    }

    /** Связывает заявление с созданным перевозчиком. */
    public void attach(TransporterEntity carrier) {
        transporter = carrier;
    }

    /** Сохраняет ожидающую команду процесса. */
    public void recordPending(String action, UUID command) {
        pendingAction = action;
        pendingCommandId = command;
    }
}
