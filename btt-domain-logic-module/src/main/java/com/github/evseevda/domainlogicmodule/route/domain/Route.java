package com.github.evseevda.domainlogicmodule.route.domain;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransportKind;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/** Маршрут между пунктами отправления и назначения для заданного вида транспорта. */
@Entity
@Getter
@Table(name = "route")
public class Route {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Пункт отправления. */
    @Column(name = "source", nullable = false, length = 255)
    private String source;

    /** Пункт назначения. */
    @Column(name = "dest", nullable = false, length = 255)
    private String dest;

    /** Продолжительность маршрута в минутах. */
    @Column(name = "duration", nullable = false)
    private int duration;

    /** Вид транспорта. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_kind_id", nullable = false)
    private TransportKind transportKind;

    protected Route() {
    }
}
