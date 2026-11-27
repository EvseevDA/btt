package com.github.evseevda.domainlogicmodule.transporter.domain;


import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Юридическое лицо, к которому относятся пользователи и перевозчики. */
@Entity
@Getter
@Table(name = "transporter_organization")
public class TransporterOrganization {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Отображаемое название. */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** ИНН организации. */
    @Column(name = "inn", nullable = false, unique = true, length = 12)
    private String inn;

    /** ОГРН организации. */
    @Column(name = "ogrn", nullable = false, unique = true, length = 15)
    private String ogrn;

    /** Юридический адрес организации. */
    @Column(name = "legal_address", nullable = false, columnDefinition = "text")
    private String legalAddress;

    /** Время создания записи в UTC. */
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected TransporterOrganization() {
    }

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public TransporterOrganization(
            UUID id,
            String name,
            String inn,
            String ogrn,
            String legalAddress,
            LocalDateTime createdAt
    ) {
        this.id = id;
        this.name = name;
        this.inn = inn;
        this.ogrn = ogrn;
        this.legalAddress = legalAddress;
        this.createdAt = createdAt;
    }
}
