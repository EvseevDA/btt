package com.github.evseevda.domainlogicmodule.ticket.repository;

import com.github.evseevda.domainlogicmodule.ticket.repository.TicketRepository.Fare;
import com.github.evseevda.domainlogic.model.TicketDto;
import com.github.evseevda.domainlogicmodule.identity.domain.UserAccount;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.ticket.domain.Ticket;
import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;
import com.github.evseevda.domainlogicmodule.transporter.domain.Transporter;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;
import com.github.evseevda.domainlogicmodule.trip.domain.Trip;
import com.github.evseevda.domainlogicmodule.trip.domain.TripSeat;
import com.github.evseevda.domainlogicmodule.trip.domain.TripStatus;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.hibernate.LockMode;
import org.hibernate.query.SelectionQuery;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Управляет доступом к билетам и блокировками инвентаря рейса. */
class TicketRepositoryCustomImpl extends JpaQueries implements TicketRepositoryCustom {
    /** Подключает репозиторий билетов и EntityManager для блокировок. */
    TicketRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Читает билет только в пределах указанного аккаунта. */
    @Override
    public Optional<TicketDto> findTicket(UUID id, UUID user) {
        return one(select("select b from Ticket b where b.id = :id and b.user.id = :user", Ticket.class)
                .setParameter("id", id).setParameter("user", user)).map(TicketRepositoryCustomImpl::dto);
    }

    /** Возвращает страницу билетов аккаунта с фильтром по рейсу. */
    @Override
    public List<TicketDto> listTickets(UUID user, UUID trip, int offset, int limit) {
        return select("""
                select b from Ticket b where b.user.id = :user and (:trip is null or b.trip.id = :trip)
                order by b.bookedAt desc, b.id
                """, Ticket.class).setParameter("user", user).setParameter("trip", trip)
                .setFirstResult(offset).setMaxResults(limit).getResultList().stream().map(TicketRepositoryCustomImpl::dto).toList();
    }

    /** Блокирует рейс и перевозчика на чтение, пока проверяются тариф и доступность. */
    @Override
    public Optional<Fare> lockFare(UUID trip) {
        Trip found = em.find(Trip.class, trip, LockModeType.PESSIMISTIC_READ);
        if (found == null) return Optional.empty();
        Transporter carrier = em.find(Transporter.class, found.getTransporter().getId(), LockModeType.PESSIMISTIC_READ);
        return Optional.of(new Fare(found.getBasePrice(), found.getStatus() == TripStatus.SCHEDULED
                && carrier.getStatus() == TransporterStatus.REGISTERED && found.getDepartureAt().isAfter(now())));
    }

    /** Сохраняет билет, если заблокированное место ещё свободно и рейс не отправился. */
    @Override
    public int insertTicket(UUID ticket, UUID trip, UUID user, String number, String seat, BigDecimal price, int status) {
        Trip found = em.find(Trip.class, trip);
        if (!found.getDepartureAt().isAfter(now()) || !select("""
                select b.id from Ticket b where b.trip.id = :trip and b.seatCode = :seat and b.status = BOOKED
                """, UUID.class).setParameter("trip", trip).setParameter("seat", seat).setMaxResults(1).getResultList().isEmpty()) {
            return 0;
        }
        insert(new Ticket(ticket, found, ref(UserAccount.class, user), number, seat, price, TicketStatus.fromId(status), now()));
        return 1;
    }

    /** Отменяет активный билет владельца до отправления рейса. */
    @Override
    public int cancelTicket(UUID id, UUID user, int booked, int cancelled) {
        var found = one(select("select b from Ticket b where b.id = :id and b.user.id = :user", Ticket.class)
                .setParameter("id", id).setParameter("user", user).setLockMode(LockModeType.PESSIMISTIC_WRITE));
        if (found.isEmpty()) return 0;
        Ticket ticket = found.orElseThrow();
        em.refresh(ticket, LockModeType.PESSIMISTIC_WRITE);
        if (ticket.getStatus().getId() != booked || !ticket.getTrip().getDepartureAt().isAfter(now())) return 0;
        ticket.cancel();
        return 1;
    }

    /** Блокирует конкретное место, ожидая освобождения блокировки при конкуренции. */
    @Override
    public Optional<String> lockRequestedSeat(UUID trip, String code) {
        return Optional.ofNullable(em.find(TripSeat.class, new TripSeat.Key(trip, code), LockModeType.PESSIMISTIC_WRITE))
                .map(seat -> seat.getId().seatCode());
    }

    /** Блокирует первое свободное место, пропуская места, занятые другой транзакцией. */
    @Override
    public Optional<String> lockAvailableSeat(UUID trip, int booked) {
        var query = select("""
                select s from TripSeat s where s.id.tripId = :trip
                    and not exists (select b.id from Ticket b where b.trip.id = :trip
                        and b.seatCode = s.id.seatCode and b.status = :booked)
                order by s.id.seatCode
                """, TripSeat.class).setParameter("trip", trip).setParameter("booked", TicketStatus.fromId(booked))
                .setMaxResults(1);
        query.unwrap(SelectionQuery.class).setHibernateLockMode(LockMode.UPGRADE_SKIPLOCKED);
        return one(query).map(seat -> seat.getId().seatCode());
    }

    private static TicketDto dto(Ticket ticket) {
        return new TicketDto(ticket.getId(), ticket.getTrip().getId(), ticket.getUser().getId(), ticket.getTicketNumber(),
                ticket.getPrice(), ticket.getStatus().getId(), ticket.getBookedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .seatCode(ticket.getSeatCode());
    }

}
