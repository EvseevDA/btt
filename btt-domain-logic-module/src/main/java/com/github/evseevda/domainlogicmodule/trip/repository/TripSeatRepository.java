package com.github.evseevda.domainlogicmodule.trip.repository;

import com.github.evseevda.domainlogicmodule.trip.domain.TripSeat;
import org.springframework.data.jpa.repository.JpaRepository;

/** Хранит инвентарь мест конкретного рейса. */
public interface TripSeatRepository extends JpaRepository<TripSeat, TripSeat.Key> {
}
