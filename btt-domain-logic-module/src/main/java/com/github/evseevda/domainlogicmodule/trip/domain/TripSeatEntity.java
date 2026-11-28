package com.github.evseevda.domainlogicmodule.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** Место в инвентаре рейса. Доступность определяется активными билетами. */
@Entity
@Getter
@Table(name = "trip_seat")
public class TripSeatEntity {
    /** Идентификатор записи. */
    @EmbeddedId private Key id;

    protected TripSeatEntity() {}

    /** Создаёт место рейса с указанным кодом. */
    public TripSeatEntity(UUID tripId, String seatCode) {
        this.id = new Key(tripId, seatCode);
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
            @Column(name = "seat_code", nullable = false, length = 20) String seatCode)
            implements java.io.Serializable {}
}
