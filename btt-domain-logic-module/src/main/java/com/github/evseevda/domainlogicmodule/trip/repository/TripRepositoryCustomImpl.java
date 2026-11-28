package com.github.evseevda.domainlogicmodule.trip.repository;

import com.github.evseevda.domainlogic.model.TripDto;
import com.github.evseevda.domainlogic.model.TripSeatDto;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.trip.domain.TripEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Запрашивает расписание и показывает актуальную доступность мест. */
class TripRepositoryCustomImpl extends JpaQueries implements TripRepositoryCustom {
    private static final String SALE_OPEN =
            """
            t.status = com.github.evseevda.domainlogicmodule.trip.domain.TripStatus.SCHEDULED
            and c.status = com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus.REGISTERED
            and t.basePrice is not null and t.departureAt > :now
            """;
    private static final String FREE_SEAT =
            """
            not exists (select b.id from TicketEntity b where b.trip.id = t.id
              and b.seatCode = s.id.seatCode and b.status = BOOKED)
            """;
    private static final String FREE_COUNT =
            """
            (select count(s) from TripSeatEntity s where s.id.tripId = t.id and
            """
                    + FREE_SEAT
                    + ")";
    private static final String TRIPS =
            """
            select t as trip, case when
            """
                    + SALE_OPEN
                    + " then "
                    + FREE_COUNT
                    + """
             else 0 end as free
            from TripEntity t join t.route r join t.transporter c join c.organization o
            """;

    /** Подключает репозиторий рейсов и EntityManager для поиска. */
    TripRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Возвращает карточку рейса с актуальным числом свободных мест. */
    @Override
    public Optional<TripDto> findTripById(UUID id) {
        return findSingleResult(
                        createTypedQuery(TRIPS + " where t.id = :id", Tuple.class)
                                .setParameter("id", id)
                                .setParameter("now", getCurrentUtcDateTime()))
                .map(TripRepositoryCustomImpl::toDto);
    }

    /** Возвращает страницу расписания с применением фильтров. */
    @Override
    public List<TripDto> findAllTripsMatchingFilters(
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
            int limit) {
        return createTypedQuery(
                        TRIPS
                                + """
                where (:route is null or r.id = :route)
                  and (:transporter is null or c.id = :transporter)
                  and (:kind is null or r.transportKind.id = :kind)
                  and (:source = '' or locate(lower(:source), lower(r.source)) > 0)
                  and (:dest = '' or locate(lower(:dest), lower(r.dest)) > 0)
                  and (:carrier = '' or locate(lower(:carrier), lower(o.name)) > 0)
                  and (cast(:from as LocalDateTime) is null or t.departureAt >= :from)
                  and (cast(:until as LocalDateTime) is null or t.departureAt < :until)
                  and (:maxPrice is null or t.basePrice <= :maxPrice)
                  and (:bookable = false or (
                """
                                + SALE_OPEN
                                + " and "
                                + FREE_COUNT
                                + " > 0)) order by t.departureAt, t.id",
                        Tuple.class)
                .setParameter("route", route)
                .setParameter("transporter", transporter)
                .setParameter("kind", kind)
                .setParameter("source", source)
                .setParameter("dest", destination)
                .setParameter("carrier", carrier)
                .setParameter("from", from)
                .setParameter("until", until)
                .setParameter("maxPrice", maxPrice)
                .setParameter("bookable", bookable)
                .setParameter("now", getCurrentUtcDateTime())
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(TripRepositoryCustomImpl::toDto)
                .toList();
    }

    /** Проверяет, существует ли рейс для запроса списка мест. */
    @Override
    public boolean existsTripById(UUID id) {
        return em.find(TripEntity.class, id) != null;
    }

    /** Возвращает места рейса с признаком доступности для бронирования. */
    @Override
    public List<TripSeatDto> findAllSeatsByTripId(UUID id) {
        return createTypedQuery(
                        "select s.id.seatCode as code, case when "
                                + SALE_OPEN
                                + " and "
                                + FREE_SEAT
                                + """
                 then true else false end as available
                from TripEntity t join t.transporter c, TripSeatEntity s
                where t.id = :id and s.id.tripId = t.id order by s.id.seatCode
                """,
                        Tuple.class)
                .setParameter("id", id)
                .setParameter("now", getCurrentUtcDateTime())
                .getResultList()
                .stream()
                .map(
                        row ->
                                new TripSeatDto(
                                        row.get("code", String.class),
                                        row.get("available", Boolean.class)))
                .toList();
    }

    private static TripDto toDto(Tuple row) {
        var trip = row.get("trip", TripEntity.class);
        return new TripDto(
                        trip.getId(),
                        trip.getRoute().getId(),
                        trip.getTransporter().getId(),
                        trip.getDepartureAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        trip.getArrivalAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                        trip.getStatus().getId())
                .basePrice(trip.getBasePrice())
                .availableSeats(((Number) row.get("free")).intValue());
    }
}
