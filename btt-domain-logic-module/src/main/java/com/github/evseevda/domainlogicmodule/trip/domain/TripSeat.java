package com.github.evseevda.domainlogicmodule.trip.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

@Entity
@Getter
@Table(name = "trip_seat")
public class TripSeat {
    @EmbeddedId
    private Key id;

    protected TripSeat() {
    }

    @jakarta.persistence.Embeddable
    public record Key(
            @Column(name = "trip_id", nullable = false) UUID tripId,
            @Column(name = "seat_code", nullable = false, length = 20) String seatCode
    ) implements java.io.Serializable {
    }
}
