package com.github.evseevda.domainlogicmodule.trip.repository;

import com.github.evseevda.domainlogicmodule.trip.domain.TripSeatEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Хранит инвентарь мест конкретного рейса. */
public interface TripSeatRepository extends JpaRepository<TripSeatEntity, TripSeatEntity.Key> {}
