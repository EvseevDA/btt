package com.github.evseevda.domainlogicmodule.transporter.domain;

import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterStatusConverter;

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

/** Постоянная запись перевозчика с организацией и текущим состоянием регистрации. */
@Entity
@Getter
@Table(name = "transporter")
public class Transporter {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Организация перевозчика. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "organization_id", nullable = false)
    private TransporterOrganization organization;

    /** Вид транспорта. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_kind_id", nullable = false)
    private TransportKind transportKind;

    /** Текущее состояние записи. */
    @Convert(converter = TransporterStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TransporterStatus status;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Transporter() {
    }
}
