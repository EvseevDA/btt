package com.github.evseevda.domainlogicmodule.transporter.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/** Справочная запись вида транспорта. */
@Entity
@Getter
@Table(name = "transport_kind")
public class TransportKind {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Отображаемое название. */
    @Column(name = "name", nullable = false, unique = true, length = 100)
    private String name;

    /** Описание записи. */
    @Column(name = "description", columnDefinition = "text")
    private String description;

    protected TransportKind() {
    }
}
