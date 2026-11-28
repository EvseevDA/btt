package com.github.evseevda.domainlogicmodule.ticket.domain;

import com.github.evseevda.domainlogicmodule.identity.domain.UserAccountEntity;
import com.github.evseevda.domainlogicmodule.ticket.conversion.TicketStatusConverter;
import com.github.evseevda.domainlogicmodule.trip.domain.TripEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

/** Билет пользователя с зафиксированными местом, ценой и состоянием брони. */
@Entity
@Getter
@Table(name = "ticket")
public class TicketEntity {
    /** Идентификатор записи. */
    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    /** Рейс, на который выдан билет. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "trip_id", nullable = false)
    private TripEntity trip;

    /** Владелец билета. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccountEntity user;

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

    protected TicketEntity() {}

    /** Создаёт новую запись с уже выделенным идентификатором. */
    public TicketEntity(
            UUID id,
            TripEntity trip,
            UserAccountEntity user,
            String ticketNumber,
            String seatCode,
            BigDecimal price,
            TicketStatus status,
            LocalDateTime bookedAt) {
        this.id = id;
        this.trip = trip;
        this.user = user;
        this.ticketNumber = ticketNumber;
        this.seatCode = seatCode;
        this.price = price;
        this.status = status;
        this.bookedAt = bookedAt;
    }

    /** Отменяет активную бронь. */
    public void cancel() {
        status = TicketStatus.CANCELLED;
    }
}
