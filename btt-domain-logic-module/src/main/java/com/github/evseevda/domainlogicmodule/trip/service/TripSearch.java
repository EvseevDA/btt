package com.github.evseevda.domainlogicmodule.trip.service;

import com.github.evseevda.domainlogic.model.TripDto;
import com.github.evseevda.domainlogic.model.TripSeatDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.trip.repository.TripRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

/** Читает расписание и вычисляет доступность мест на момент запроса. */
@Service
@Profile("!mock-api")
public class TripSearch {
    private final TripRepository repository;

    /** Создаёт сервис с доступом к базе данных. */
    public TripSearch(TripRepository repository) {
        this.repository = repository;
    }

    /** Читает рейс по идентификатору и рассчитывает доступность мест. */
    public TripDto get(UUID id) {
        return repository.findTrip(id).orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Рейс не найден"));
    }

    /** Ищет рейсы по фильтрам и возвращает страницу в порядке отправления. */
    public List<TripDto> list(Filter filter, int offset, int limit) {
        if (filter.from() != null && filter.until() != null && !filter.from().isBefore(filter.until())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный интервал отправления");
        }
        return repository.searchTrips(filter.route(), filter.transporter(), filter.kind(), text(filter.source()), text(filter.destination()), text(filter.carrier()), utc(filter.from()), utc(filter.until()), filter.maxPrice(), filter.bookable(), offset, limit);
    }

    /** Возвращает места рейса и доступность каждого места на момент чтения. */
    public List<TripSeatDto> seats(UUID id) {
        if (!repository.exists(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Рейс не найден");
        }
        return repository.listSeats(id);
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static LocalDateTime utc(OffsetDateTime time) {
        return time == null ? null : time.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
    }

    /**
     * Критерии поиска рейсов. Пропущенные необязательные поля не ограничивают выборку.
     *
     * @param route идентификатор маршрута
     * @param transporter идентификатор перевозчика
     * @param kind идентификатор вида транспорта
     * @param source фрагмент названия пункта отправления
     * @param destination фрагмент названия пункта назначения
     * @param carrier фрагмент названия организации перевозчика
     * @param from нижняя граница отправления включительно
     * @param until верхняя граница отправления исключительно
     * @param maxPrice максимальный тариф
     * @param bookable признак отбора будущих рейсов, доступных для бронирования
     */
    public record Filter(UUID route, UUID transporter, UUID kind, String source, String destination, String carrier,
                         OffsetDateTime from, OffsetDateTime until, BigDecimal maxPrice, boolean bookable) {
    }
}
