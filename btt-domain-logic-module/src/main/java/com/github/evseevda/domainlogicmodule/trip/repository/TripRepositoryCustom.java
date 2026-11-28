package com.github.evseevda.domainlogicmodule.trip.repository;

import com.github.evseevda.domainlogic.model.TripDto;
import com.github.evseevda.domainlogic.model.TripSeatDto;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface TripRepositoryCustom {
    /** Возвращает карточку рейса с актуальным числом свободных мест. */
    Optional<TripDto> findTripById(UUID id);

    /** Возвращает страницу расписания с применением фильтров. */
    List<TripDto> findAllTripsMatchingFilters(
            UUID route,
            UUID transporter,
            UUID kind,
            String source,
            String destination,
            String carrier,
            LocalDateTime from,
            LocalDateTime until,
            BigDecimal maxPrice,
            boolean bookable,
            int offset,
            int limit);

    /** Проверяет, существует ли рейс для запроса списка мест. */
    boolean existsTripById(UUID id);

    /** Возвращает места рейса с признаком доступности для бронирования. */
    List<TripSeatDto> findAllSeatsByTripId(UUID id);
}
