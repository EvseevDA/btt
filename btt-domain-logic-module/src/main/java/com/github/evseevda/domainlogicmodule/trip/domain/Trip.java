package com.github.evseevda.domainlogicmodule.trip.domain;

import com.github.evseevda.domainlogicmodule.route.domain.Route;
import com.github.evseevda.domainlogicmodule.transporter.domain.Transporter;
import com.github.evseevda.domainlogicmodule.trip.conversion.TripStatusConverter;

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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/** Запланированный рейс перевозчика по маршруту с временем отправления и тарифом. */
@Entity
@Getter
@Table(name = "trip")
public class Trip {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Маршрут рейса. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private Route route;

    /** Перевозчик, к которому относится запись. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transporter_id", nullable = false)
    private Transporter transporter;

    /** Время отправления в UTC. */
    @Column(name = "departure_at", nullable = false)
    private LocalDateTime departureAt;

    /** Время прибытия в UTC. */
    @Column(name = "arrival_at", nullable = false)
    private LocalDateTime arrivalAt;

    /** Текущее состояние записи. */
    @Convert(converter = TripStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TripStatus status;

    /** Тариф рейса при наличии. */
    @Column(name = "base_price", precision = 12, scale = 2)
    private BigDecimal basePrice;

    protected Trip() {
    }

    /** Планирует рейс по маршруту перевозчика. */
    public Trip(Route route, Transporter transporter, LocalDateTime departureAt, LocalDateTime arrivalAt, BigDecimal basePrice) {
        this.route = route;
        this.transporter = transporter;
        this.departureAt = departureAt;
        this.arrivalAt = arrivalAt;
        this.basePrice = basePrice;
        this.status = TripStatus.SCHEDULED;
    }
}
