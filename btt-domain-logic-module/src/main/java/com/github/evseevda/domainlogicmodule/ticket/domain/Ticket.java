package com.github.evseevda.domainlogicmodule.ticket.domain;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.ticket.conversion.TicketStatusConverter;
import com.github.evseevda.domainlogicmodule.trip.domain.Trip;

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

/** Билет пользователя с зафиксированными местом, ценой и состоянием брони. */
@Entity
@Getter
@Table(name = "ticket")
public class Ticket {
    /** Идентификатор записи. */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Рейс, на который выдан билет. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private Trip trip;

    /** Владелец билета. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    /** Номер билета. */
    @Column(name = "ticket_number", nullable = false, unique = true, length = 100)
    private String ticketNumber;

    /** Код места в пределах рейса. */
    @Column(name = "seat_code", length = 20)
    private String seatCode;

    /** Цена, зафиксированная при бронировании. */
    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    /** Текущее состояние записи. */
    @Convert(converter = TicketStatusConverter.class)
    @Column(name = "status", nullable = false)
    private TicketStatus status;

    /** Время бронирования в UTC. */
    @Column(name = "booked_at", nullable = false)
    private LocalDateTime bookedAt;

    protected Ticket() {
    }
}
