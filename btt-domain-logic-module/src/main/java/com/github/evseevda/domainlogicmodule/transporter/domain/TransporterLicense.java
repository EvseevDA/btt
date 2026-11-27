package com.github.evseevda.domainlogicmodule.transporter.domain;

import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterLicenseStatusConverter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDate;
import java.util.UUID;

/** Запись лицензии в конкретном снимке перевозчика. */
@Entity
@Getter
@Table(name = "transporter_license")
public class TransporterLicense {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Перевозчик, к которому относится запись. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false)
    private Transporter transporter;

    /** Номер лицензии. */
    @Column(name = "license_number", nullable = false, length = 100)
    private String licenseNumber;

    /** Дата выдачи лицензии. */
    @Column(name = "issued_at", nullable = false)
    private LocalDate issuedAt;

    /** Дата окончания действия лицензии. */
    @Column(name = "expires_at", nullable = false)
    private LocalDate expiresAt;

    /** Орган, выдавший лицензию. */
    @Column(name = "issuing_authority", nullable = false, length = 255)
    private String issuingAuthority;

    /** Текущее состояние записи. */
    @Convert(converter = TransporterLicenseStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TransporterLicenseStatus status;

    /** Снимок перевозчика, к которому относится запись. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_version_id", nullable = false)
    private TransporterVersion transporterVersion;

    protected TransporterLicense() {
    }

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public TransporterLicense(
            UUID id,
            Transporter transporter,
            TransporterVersion transporterVersion,
            String licenseNumber,
            LocalDate issuedAt,
            LocalDate expiresAt,
            String issuingAuthority,
            TransporterLicenseStatus status
    ) {
        this.id = id;
        this.transporter = transporter;
        this.transporterVersion = transporterVersion;
        this.licenseNumber = licenseNumber;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.issuingAuthority = issuingAuthority;
        this.status = status;
    }
}
