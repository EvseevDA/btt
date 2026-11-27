package com.github.evseevda.domainlogicmodule.ticket.service;

import com.github.evseevda.domainlogic.model.BookTicket;
import com.github.evseevda.domainlogic.model.TicketDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.ticket.repository.TicketRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/** Управляет билетами текущего пользователя и резервирует места транзакционно. */
@Service
@Profile("!mock-api")
public class TicketService {
    private final TicketRepository repository;

    /** Создаёт сервис с доступом к базе данных. */
    public TicketService(TicketRepository repository) {
        this.repository = repository;
    }

    /** Читает билет только в пределах аккаунта текущего пользователя. */
    public TicketDto get(UUID id) {
        return repository.findTicket(id, CurrentAccount.id()).orElseThrow(TicketService::notFound);
    }

    /** Читает билеты текущего пользователя с фильтром по рейсу. */
    public List<TicketDto> list(UUID trip, int offset, int limit) {
        return repository.listTickets(CurrentAccount.id(), trip, offset, limit);
    }

    /** Бронирует выбранное или первое доступное место, фиксируя цену рейса в билете. */
    @Transactional
    public TicketDto book(BookTicket form) {
        UUID user = CurrentAccount.id();
        UUID trip = form.getTripId();
        var fare = repository.lockFare(trip).orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Рейс не найден"));
        if (!fare.bookable() || fare.price() == null) throw conflict("Бронирование рейса недоступно");

        String seat = lockSeat(trip, form.getSeatCode());
        UUID ticket = UUID.randomUUID();
        try {
            int inserted = repository.insertTicket(ticket, trip, user, "BTT-" + ticket, seat, fare.price(), TicketStatus.BOOKED.getId());
            if (inserted == 0) throw conflict("Место занято или рейс уже отправился");
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Место уже забронировано", ex);
        }
        return get(ticket);
    }

    /** Отменяет билет его владельцем до отправления. Повторная отмена возвращает тот же результат. */
    @Transactional
    public TicketDto cancel(UUID id) {
        UUID user = CurrentAccount.id();
        var ticket = get(id);
        if (ticket.getStatus() == TicketStatus.CANCELLED.getId()) return ticket;
        int changed = repository.cancelTicket(id, user, TicketStatus.BOOKED.getId(), TicketStatus.CANCELLED.getId());
        var updated = get(id);
        if (changed == 0 && updated.getStatus() != TicketStatus.CANCELLED.getId()) {
            throw conflict("Отмена доступна до отправления рейса");
        }
        return updated;
    }

    private String lockSeat(UUID trip, String code) {
        if (code != null && !code.isBlank()) {
            return repository.lockRequestedSeat(trip, code.strip())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Место рейса не найдено"));
        }
        return repository.lockAvailableSeat(trip, TicketStatus.BOOKED.getId()).orElseThrow(() -> conflict("Нет свободных мест"));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Билет не найден");
    }

    private static ResponseStatusException conflict(String message) {
        return new ResponseStatusException(HttpStatus.CONFLICT, message);
    }

}
