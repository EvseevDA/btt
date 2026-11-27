package com.github.evseevda.domainlogicmodule.trip.repository;

import com.github.evseevda.domainlogicmodule.trip.domain.Trip;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Запрашивает расписание и показывает актуальную доступность мест. */
public interface TripRepository extends JpaRepository<Trip, UUID>, TripRepositoryCustom {
}
