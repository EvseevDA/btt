package com.github.evseevda.domainlogicmodule.ticket.service;

import com.github.evseevda.domainlogic.model.BookTicket;
import com.github.evseevda.domainlogic.model.TicketDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!mock-api")
public class TicketService {
    private final JdbcClient db;

    public TicketService(JdbcClient db) {
        this.db = db;
    }

    public TicketDto get(UUID id) {
        return db.sql("SELECT * FROM ticket WHERE id = :id AND user_id = :user")
                .param("id", id).param("user", CurrentAccount.id())
                .query((rs, row) -> ticket(rs)).optional().orElseThrow(TicketService::notFound);
    }

    public List<TicketDto> list(UUID trip, int offset, int limit) {
        return db.sql("""
                SELECT * FROM ticket WHERE user_id = :user AND (:trip::uuid IS NULL OR trip_id = :trip::uuid)
                ORDER BY booked_at DESC, id LIMIT :limit OFFSET :offset
                """).param("user", CurrentAccount.id()).param("trip", trip).param("offset", offset).param("limit", limit)
                .query((rs, row) -> ticket(rs)).list();
    }

    @Transactional
    public TicketDto book(BookTicket form) {
        UUID user = CurrentAccount.id();
        UUID trip = form.getTripId();
        var fare = db.sql("""
                SELECT t.base_price, t.status = 1 AND c.status = 1
                    AND t.departure_at > timezone('UTC', clock_timestamp()) AS bookable
                FROM trip t JOIN transporter c ON c.id = t.transporter_id WHERE t.id = :id FOR SHARE OF t, c
                """).param("id", trip).query((rs, row) -> new Fare(rs.getBigDecimal("base_price"),
                        rs.getBoolean("bookable"))).optional().orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Рейс не найден"));
        if (!fare.bookable() || fare.price() == null) throw conflict("Бронирование рейса недоступно");

        String seat = lockSeat(trip, form.getSeatCode());
        UUID ticket = UUID.randomUUID();
        try {
            int inserted = db.sql("""
                    INSERT INTO ticket (id, trip_id, user_id, ticket_number, seat_code, price, status, booked_at)
                    SELECT :id, :trip, :user, :number, :seat, :price, :status, timezone('UTC', clock_timestamp())
                    WHERE NOT EXISTS (SELECT 1 FROM ticket b WHERE b.trip_id = :trip AND b.seat_code = :seat AND b.status = :status)
                      AND EXISTS (SELECT 1 FROM trip t WHERE t.id = :trip
                          AND t.departure_at > timezone('UTC', clock_timestamp()))
                    """).param("id", ticket).param("trip", trip).param("user", user).param("number", "BTT-" + ticket)
                    .param("seat", seat).param("price", fare.price()).param("status", TicketStatus.BOOKED.getId()).update();
            if (inserted == 0) throw conflict("Место занято или рейс уже отправился");
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Место уже забронировано", ex);
        }
        return get(ticket);
    }

    @Transactional
    public TicketDto cancel(UUID id) {
        UUID user = CurrentAccount.id();
        var ticket = get(id);
        if (ticket.getStatus() == TicketStatus.CANCELLED.getId()) return ticket;
        int changed = db.sql("""
                UPDATE ticket b SET status = :cancelled FROM trip t
                WHERE b.id = :id AND b.user_id = :user AND b.trip_id = t.id AND b.status = :booked
                  AND t.departure_at > timezone('UTC', clock_timestamp())
                """).param("id", id).param("user", user).param("booked", TicketStatus.BOOKED.getId())
                .param("cancelled", TicketStatus.CANCELLED.getId()).update();
        var updated = get(id);
        if (changed == 0 && updated.getStatus() != TicketStatus.CANCELLED.getId()) {
            throw conflict("Отмена доступна до отправления рейса");
        }
        return updated;
    }

    private String lockSeat(UUID trip, String code) {
        if (code != null && !code.isBlank()) {
            return db.sql("SELECT seat_code FROM trip_seat WHERE trip_id = :trip AND seat_code = :code FOR UPDATE")
                    .param("trip", trip).param("code", code.strip()).query(String.class).optional()
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Место рейса не найдено"));
        }
        return db.sql("""
                SELECT s.seat_code FROM trip_seat s WHERE s.trip_id = :trip
                    AND NOT EXISTS (SELECT 1 FROM ticket b WHERE b.trip_id = s.trip_id
                        AND b.seat_code = s.seat_code AND b.status = :booked)
                ORDER BY s.seat_code LIMIT 1 FOR UPDATE SKIP LOCKED
                """).param("trip", trip).param("booked", TicketStatus.BOOKED.getId()).query(String.class)
                .optional().orElseThrow(() -> conflict("Нет свободных мест"));
    }

    private static TicketDto ticket(ResultSet rs) throws SQLException {
        return new TicketDto(rs.getObject("id", UUID.class), rs.getObject("trip_id", UUID.class),
                rs.getObject("user_id", UUID.class), rs.getString("ticket_number"), rs.getBigDecimal("price"),
                rs.getInt("status"), rs.getObject("booked_at", LocalDateTime.class)
                        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)).seatCode(rs.getString("seat_code"));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Билет не найден");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

    private record Fare(BigDecimal price, boolean bookable) {
    }
}
