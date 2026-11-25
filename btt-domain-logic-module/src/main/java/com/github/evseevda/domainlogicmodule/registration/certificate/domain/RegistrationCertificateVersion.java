package com.github.evseevda.domainlogicmodule.registration.certificate.domain;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersion;
import com.github.evseevda.domainlogicmodule.registration.certificate.conversion.RegistrationCertificateVersionStatusConverter;

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
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "transporter_registration_certificate_version",
        uniqueConstraints = @UniqueConstraint(name = "uq_certificate_version",
                columnNames = {"certificate_id", "version_number"}))
public class RegistrationCertificateVersion {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "certificate_id", nullable = false)
    private RegistrationCertificate certificate;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Convert(converter = RegistrationCertificateVersionStatusConverter.class)
    @Column(name = "status", nullable = false)
    private RegistrationCertificateVersionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "source_version_id", nullable = true)
    private RegistrationCertificateVersion sourceVersion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_version_id", nullable = true)
    private RegistrationCertificateVersion previousVersion;

    @Column(name = "change_description", columnDefinition = "text")
    private String changeDescription;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_version_id", nullable = false)
    private TransporterVersion transporterVersion;

    protected RegistrationCertificateVersion() {
    }
}
