package com.github.evseevda.domainlogicmodule.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/** Место в инвентаре рейса. Доступность определяется активными билетами. */
@Entity
@Getter
@Table(name = "trip_seat")
public class TripSeat {
    /** Идентификатор записи. */
    @EmbeddedId
    private Key id;

    protected TripSeat() {
    }

    /**
     * Составной ключ места, уникального в пределах рейса.
     *
     * @param tripId идентификатор рейса
     * @param seatCode код места в пределах рейса
     */
    @jakarta.persistence.Embeddable
    public record Key(
            @Column(name = "trip_id", nullable = false) UUID tripId,
            @Column(name = "seat_code", nullable = false, length = 20) String seatCode
    ) implements java.io.Serializable {
    }
}
