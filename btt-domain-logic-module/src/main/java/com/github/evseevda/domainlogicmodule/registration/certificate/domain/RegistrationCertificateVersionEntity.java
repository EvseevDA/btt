package com.github.evseevda.domainlogicmodule.registration.certificate.domain;

import com.github.evseevda.domainlogicmodule.registration.certificate.conversion.RegistrationCertificateVersionStatusConverter;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersionEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

/** Версия регистрационного удостоверения, связанная со снимком перевозчика. */
@Entity
@Getter
@Table(
        name = "transporter_registration_certificate_version",
        uniqueConstraints =
                @UniqueConstraint(
                        name = "uq_certificate_version",
                        columnNames = {"certificate_id", "version_number"}))
public class RegistrationCertificateVersionEntity {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Регистрационное удостоверение, к которому относится версия. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private RegistrationCertificateEntity certificate;

    /** Порядковый номер версии. */
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** Текущее состояние записи. */
    @Convert(converter = RegistrationCertificateVersionStatusConverter.class)
    @Column(name = "status", nullable = false)
    private RegistrationCertificateVersionStatus status;

    /** Версия РУ, послужившая основой для изменений. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_version_id", nullable = true)
    private RegistrationCertificateVersionEntity sourceVersion;

    /** Предыдущая запись в истории версий РУ. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_version_id", nullable = true)
    private RegistrationCertificateVersionEntity previousVersion;

    /** Описание изменений. */
    @Column(name = "change_description", columnDefinition = "text")
    private String changeDescription;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** Снимок перевозчика, к которому относится запись. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_version_id", nullable = false)
    private TransporterVersionEntity transporterVersion;

    protected RegistrationCertificateVersionEntity() {}

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public RegistrationCertificateVersionEntity(
            UUID id,
            RegistrationCertificateEntity certificate,
            TransporterVersionEntity transporterVersion,
            int versionNumber,
            RegistrationCertificateVersionStatus status,
            RegistrationCertificateVersionEntity previousVersion,
            String changeDescription,
            LocalDateTime createdAt) {
        this.id = id;
        this.certificate = certificate;
        this.transporterVersion = transporterVersion;
        this.versionNumber = versionNumber;
        this.status = status;
        this.previousVersion = previousVersion;
        this.changeDescription = changeDescription;
        this.createdAt = createdAt;
        this.sourceVersion = previousVersion;
    }

    /** Меняет состояние версии РУ. */
    public void changeStatus(RegistrationCertificateVersionStatus state) {
        status = state;
    }
}
