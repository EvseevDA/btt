package com.github.evseevda.domainlogicmodule.transporter.domain;

import com.github.evseevda.domainlogicmodule.transporter.conversion.TransporterStatusConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.FetchType;
import jakarta.persistence.Convert;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Getter
@Table(name = "transporter_version")
public class TransporterVersion {
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false)
    private Transporter transporter;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_kind_id", nullable = false)
    private TransportKind transportKind;

    @Convert(converter = TransporterStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TransporterStatus status;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TransporterVersion() {
    }
}
