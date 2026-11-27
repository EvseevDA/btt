package com.github.evseevda.domainlogicmodule.registration.certificate.domain;

import com.github.evseevda.domainlogicmodule.registration.certificate.conversion.RegistrationCertificateStatusConverter;
import com.github.evseevda.domainlogicmodule.transporter.domain.Transporter;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Регистрационное удостоверение перевозчика с постоянным номером. */
@Entity
@Getter
@Table(name = "transporter_registration_certificate")
public class RegistrationCertificate {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Перевозчик, к которому относится запись. */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false, unique = true)
    private Transporter transporter;

    /** Постоянный номер регистрационного удостоверения. */
    @Column(name = "certificate_number", nullable = false, unique = true, length = 100)
    private String certificateNumber;

    /** Текущее состояние записи. */
    @Convert(converter = RegistrationCertificateStatusConverter.class)
    @Column(name = "status", nullable = false)
    private RegistrationCertificateStatus status;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected RegistrationCertificate() {
    }
}
