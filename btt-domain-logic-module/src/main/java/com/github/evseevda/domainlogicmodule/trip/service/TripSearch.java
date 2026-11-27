package com.github.evseevda.domainlogicmodule.trip.service;

import com.github.evseevda.domainlogic.model.TripDto;
import com.github.evseevda.domainlogic.model.TripSeatDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Читает расписание и вычисляет доступность мест на момент запроса. */
@Service
@Profile("!mock-api")
public class TripSearch {
    private static final String SEARCH = """
            SELECT t.*, CASE WHEN t.status = 1 AND c.status = 1 AND t.base_price IS NOT NULL
                AND t.departure_at > timezone('UTC', statement_timestamp()) THEN free.seats ELSE 0 END AS available_seats
            FROM trip t
            JOIN route r ON r.id = t.route_id
            JOIN transporter c ON c.id = t.transporter_id
            JOIN transporter_organization o ON o.id = c.organization_id
            CROSS JOIN LATERAL (
                SELECT count(*) AS seats FROM trip_seat s WHERE s.trip_id = t.id AND NOT EXISTS (
                    SELECT 1 FROM ticket b WHERE b.trip_id = s.trip_id AND b.seat_code = s.seat_code AND b.status = 1
                )
            ) free
            """;

    private final JdbcClient db;

    /** Создаёт сервис с доступом к базе данных. */
    public TripSearch(JdbcClient db) {
        this.db = db;
    }

    /** Читает рейс по идентификатору и рассчитывает доступность мест. */
    public TripDto get(UUID id) {
        return db.sql(SEARCH + " WHERE t.id = :id").param("id", id)
                .query((rs, row) -> dto(rs)).optional().orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Рейс не найден"));
    }

    /** Ищет рейсы по фильтрам и возвращает страницу в порядке отправления. */
    public List<TripDto> list(Filter filter, int offset, int limit) {
        if (filter.from() != null && filter.until() != null && !filter.from().isBefore(filter.until())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный интервал отправления");
        }
        return db.sql("SELECT * FROM (" + SEARCH + """
                WHERE (:route::uuid IS NULL OR t.route_id = :route::uuid)
                  AND (:transporter::uuid IS NULL OR t.transporter_id = :transporter::uuid)
                  AND (:kind::uuid IS NULL OR r.transport_kind_id = :kind::uuid)
                  AND (:source = '' OR strpos(lower(r.source), lower(:source)) > 0)
                  AND (:dest = '' OR strpos(lower(r.dest), lower(:dest)) > 0)
                  AND (:carrier = '' OR strpos(lower(o.name), lower(:carrier)) > 0)
                  AND (:from::timestamp IS NULL OR t.departure_at >= :from::timestamp)
                  AND (:until::timestamp IS NULL OR t.departure_at < :until::timestamp)
                  AND (:maxPrice::numeric IS NULL OR t.base_price <= :maxPrice::numeric)
                ) trips WHERE (NOT :bookable OR available_seats > 0) ORDER BY departure_at, id
                LIMIT :limit OFFSET :offset
                """).param("route", filter.route()).param("transporter", filter.transporter())
                .param("kind", filter.kind()).param("source", text(filter.source())).param("dest", text(filter.destination()))
                .param("carrier", text(filter.carrier())).param("from", utc(filter.from())).param("until", utc(filter.until()))
                .param("maxPrice", filter.maxPrice()).param("bookable", filter.bookable())
                .param("offset", offset).param("limit", limit).query((rs, row) -> dto(rs)).list();
    }

    /** Возвращает места рейса и доступность каждого места на момент чтения. */
    public List<TripSeatDto> seats(UUID id) {
        get(id);
        return db.sql("""
                SELECT s.seat_code, t.status = 1 AND c.status = 1 AND t.base_price IS NOT NULL
                   AND t.departure_at > timezone('UTC', statement_timestamp())
                   AND NOT EXISTS (SELECT 1 FROM ticket b WHERE b.trip_id = s.trip_id
                       AND b.seat_code = s.seat_code AND b.status = 1) AS available
                FROM trip_seat s JOIN trip t ON t.id = s.trip_id JOIN transporter c ON c.id = t.transporter_id
                WHERE s.trip_id = :id ORDER BY s.seat_code
                """).param("id", id).query((rs, row) -> new TripSeatDto(rs.getString("seat_code"),
                        rs.getBoolean("available"))).list();
    }

    private static TripDto dto(ResultSet result) throws SQLException {
        return new TripDto(result.getObject("id", UUID.class), result.getObject("route_id", UUID.class),
                result.getObject("transporter_id", UUID.class),
                result.getObject("departure_at", LocalDateTime.class).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                result.getObject("arrival_at", LocalDateTime.class).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                result.getInt("status")).basePrice(result.getBigDecimal("base_price"))
                .availableSeats(result.getInt("available_seats"));
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
