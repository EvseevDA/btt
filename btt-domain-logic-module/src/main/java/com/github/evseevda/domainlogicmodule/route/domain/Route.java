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

@Entity
@Getter
@Table(name = "route")
public class Route {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "source", nullable = false, length = 255)
    private String source;

    @Column(name = "dest", nullable = false, length = 255)
    private String dest;

    @Column(name = "duration", nullable = false)
    private int duration;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transport_kind_id", nullable = false)
    private TransportKind transportKind;

    protected Route() {
    }
}
