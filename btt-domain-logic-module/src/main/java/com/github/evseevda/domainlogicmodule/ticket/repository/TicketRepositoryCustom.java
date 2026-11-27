package com.github.evseevda.domainlogicmodule.ticket.repository;

import com.github.evseevda.domainlogic.model.TicketDto;
import com.github.evseevda.domainlogicmodule.ticket.repository.TicketRepository.Fare;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface TicketRepositoryCustom {
    /** Читает билет только в пределах указанного аккаунта. */
    Optional<TicketDto> findTicket(UUID id, UUID user);

    /** Возвращает страницу билетов аккаунта с фильтром по рейсу. */
    List<TicketDto> listTickets(UUID user, UUID trip, int offset, int limit);

    /** Блокирует рейс и перевозчика на чтение, пока проверяются тариф и доступность. */
    Optional<Fare> lockFare(UUID trip);

    /** Сохраняет билет, если заблокированное место ещё свободно и рейс не отправился. */
    int insertTicket(UUID ticket, UUID trip, UUID user, String number, String seat, BigDecimal price, int status);

    /** Отменяет активный билет владельца до отправления рейса. */
    int cancelTicket(UUID id, UUID user, int booked, int cancelled);

    /** Блокирует конкретное место, ожидая освобождения блокировки при конкуренции. */
    Optional<String> lockRequestedSeat(UUID trip, String code);

    /** Блокирует первое свободное место, пропуская места, занятые другой транзакцией. */
    Optional<String> lockAvailableSeat(UUID trip, int booked);
}
