package com.github.evseevda.domainlogicmodule.ticket.repository;

import com.github.evseevda.domainlogic.model.TicketDto;
import com.github.evseevda.domainlogicmodule.identity.domain.UserAccountEntity;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.ticket.domain.TicketEntity;
import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;
import com.github.evseevda.domainlogicmodule.ticket.repository.TicketRepository.TripFareProjection;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;
import com.github.evseevda.domainlogicmodule.trip.domain.TripEntity;
import com.github.evseevda.domainlogicmodule.trip.domain.TripSeatEntity;
import com.github.evseevda.domainlogicmodule.trip.domain.TripStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.LockMode;
import org.hibernate.query.SelectionQuery;

/** Управляет доступом к билетам и блокировками инвентаря рейса. */
class TicketRepositoryCustomImpl extends JpaQueries implements TicketRepositoryCustom {
    /** Подключает репозиторий билетов и EntityManager для блокировок. */
    TicketRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Читает билет только в пределах указанного аккаунта. */
    @Override
    public Optional<TicketDto> findTicketByIdAndUserId(UUID id, UUID user) {
        return findSingleResult(
                        createTypedQuery(
                                        "select b from TicketEntity b where b.id = :id and b.user.id = :user",
                                        TicketEntity.class)
                                .setParameter("id", id)
                                .setParameter("user", user))
                .map(TicketRepositoryCustomImpl::toDto);
    }

    /** Возвращает страницу билетов аккаунта с фильтром по рейсу. */
    @Override
    public List<TicketDto> findAllTicketsByUserId(UUID user, UUID trip, int offset, int limit) {
        return createTypedQuery(
                        """
                select b from TicketEntity b where b.user.id = :user and (:trip is null or b.trip.id = :trip)
                order by b.bookedAt desc, b.id
                """,
                        TicketEntity.class)
                .setParameter("user", user)
                .setParameter("trip", trip)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(TicketRepositoryCustomImpl::toDto)
                .toList();
    }

    /** Блокирует рейс и перевозчика на чтение, пока проверяются тариф и доступность. */
    @Override
    public Optional<TripFareProjection> findFareByTripIdWithLock(UUID trip) {
        TripEntity found = em.find(TripEntity.class, trip, LockModeType.PESSIMISTIC_READ);
        if (found == null) {
            return Optional.empty();
        }
        TransporterEntity carrier =
                em.find(
                        TransporterEntity.class,
                        found.getTransporter().getId(),
                        LockModeType.PESSIMISTIC_READ);
        return Optional.of(
                new TripFareProjection(
                        found.getBasePrice(),
                        found.getStatus() == TripStatus.SCHEDULED
                                && carrier.getStatus() == TransporterStatus.REGISTERED
                                && found.getDepartureAt().isAfter(getCurrentUtcDateTime())));
    }

    /** Сохраняет билет, если заблокированное место ещё свободно и рейс не отправился. */
    @Override
    public int insertTicket(
            UUID ticket,
            UUID trip,
            UUID user,
            String number,
            String seat,
            BigDecimal price,
            int status) {
        TripEntity found = em.find(TripEntity.class, trip);
        if (!found.getDepartureAt().isAfter(getCurrentUtcDateTime())
                || !createTypedQuery(
                                """
                select b.id from TicketEntity b where b.trip.id = :trip and b.seatCode = :seat and b.status = BOOKED
                """,
                                UUID.class)
                        .setParameter("trip", trip)
                        .setParameter("seat", seat)
                        .setMaxResults(1)
                        .getResultList()
                        .isEmpty()) {
            return 0;
        }
        persistAndFlush(
                new TicketEntity(
                        ticket,
                        found,
                        getReference(UserAccountEntity.class, user),
                        number,
                        seat,
                        price,
                        TicketStatus.fromId(status),
                        getCurrentUtcDateTime()));
        return 1;
    }

    /** Отменяет активный билет владельца до отправления рейса. */
    @Override
    public int cancelTicket(UUID id, UUID user, int booked, int cancelled) {
        var found =
                findSingleResult(
                        createTypedQuery(
                                        "select b from TicketEntity b where b.id = :id and b.user.id = :user",
                                        TicketEntity.class)
                                .setParameter("id", id)
                                .setParameter("user", user)
                                .setLockMode(LockModeType.PESSIMISTIC_WRITE));
        if (found.isEmpty()) {
            return 0;
        }
        TicketEntity ticket = found.orElseThrow();
        em.refresh(ticket, LockModeType.PESSIMISTIC_WRITE);
        if (ticket.getStatus().getId() != booked
                || !ticket.getTrip().getDepartureAt().isAfter(getCurrentUtcDateTime())) {
            return 0;
        }
        ticket.cancel();
        return 1;
    }

    /** Блокирует конкретное место, ожидая освобождения блокировки при конкуренции. */
    @Override
    public Optional<String> findSeatCodeByTripIdAndCodeWithLock(UUID trip, String code) {
        return Optional.ofNullable(
                        em.find(
                                TripSeatEntity.class,
                                new TripSeatEntity.Key(trip, code),
                                LockModeType.PESSIMISTIC_WRITE))
                .map(seat -> seat.getId().seatCode());
    }

    /** Блокирует первое свободное место, пропуская места, занятые другой транзакцией. */
    @Override
    public Optional<String> findAvailableSeatCodeByTripIdWithLock(UUID trip, int booked) {
        var query =
                createTypedQuery(
                                """
                select s from TripSeatEntity s where s.id.tripId = :trip
                    and not exists (select b.id from TicketEntity b where b.trip.id = :trip
                        and b.seatCode = s.id.seatCode and b.status = :booked)
                order by s.id.seatCode
                """,
                                TripSeatEntity.class)
                        .setParameter("trip", trip)
                        .setParameter("booked", TicketStatus.fromId(booked))
                        .setMaxResults(1);
        query.unwrap(SelectionQuery.class).setHibernateLockMode(LockMode.UPGRADE_SKIPLOCKED);
        return findSingleResult(query).map(seat -> seat.getId().seatCode());
    }

    private static TicketDto toDto(TicketEntity ticket) {
        return new TicketDto(
                        ticket.getId(),
                        ticket.getTrip().getId(),
                        ticket.getUser().getId(),
                        ticket.getTicketNumber(),
                        ticket.getPrice(),
                        ticket.getStatus().getId(),
                        ticket.getBookedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .seatCode(ticket.getSeatCode());
    }
}
