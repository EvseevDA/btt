package com.github.evseevda.domainlogicmodule.transporter.domain;

import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterStatusConverter;
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

/** Снимок вида транспорта и статуса перевозчика на момент версии регистрации. */
@Entity
@Getter
@Table(name = "transporter_version")
public class TransporterVersionEntity {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Перевозчик, к которому относится запись. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false)
    private TransporterEntity transporter;

    /** Порядковый номер версии. */
    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    /** Вид транспорта. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_kind_id", nullable = false)
    private TransportKindEntity transportKind;

    /** Текущее состояние записи. */
    @Convert(converter = TransporterStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TransporterStatus status;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TransporterVersionEntity() {}

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public TransporterVersionEntity(
            UUID id,
            TransporterEntity transporter,
            int versionNumber,
            TransportKindEntity transportKind,
            TransporterStatus status,
            LocalDateTime createdAt) {
        this.id = id;
        this.transporter = transporter;
        this.versionNumber = versionNumber;
        this.transportKind = transportKind;
        this.status = status;
        this.createdAt = createdAt;
    }
}
