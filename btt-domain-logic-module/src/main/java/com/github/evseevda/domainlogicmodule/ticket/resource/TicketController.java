package com.github.evseevda.domainlogicmodule.ticket.resource;

import com.github.evseevda.domainlogic.api.TicketsApi;
import com.github.evseevda.domainlogic.model.BookTicket;
import com.github.evseevda.domainlogic.model.TicketDto;
import com.github.evseevda.domainlogicmodule.ticket.service.TicketService;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Предоставляет REST-операции чтения, бронирования и отмены билетов. */
@RestController
public class TicketController implements TicketsApi {
    private final ObjectProvider<TicketService> services;

    /** Создаёт контроллер и связывает его с прикладным сервисом. */
    public TicketController(ObjectProvider<TicketService> services) {
        this.services = services;
    }

    /** Создаёт билет на выбранное или автоматически назначенное место. */
    @Override
    public ResponseEntity<TicketDto> bookTicket(BookTicket bookTicket) {
        var service = services.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.bookTicket(bookTicket));
    }

    /** Освобождает место при отмене билета его владельцем. */
    @Override
    public ResponseEntity<TicketDto> cancelTicket(UUID ticketId) {
        var service = services.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.cancelTicket(ticketId));
    }

    /** Возвращает билет текущего пользователя. */
    @Override
    public ResponseEntity<TicketDto> getTicket(UUID id) {
        var service = services.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.getTicketById(id));
    }

    /** Возвращает страницу билетов текущего пользователя с необязательным фильтром по рейсу. */
    @Override
    public ResponseEntity<List<TicketDto>> listTickets(UUID tripId, Integer offset, Integer limit) {
        var service = services.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.findAllTickets(tripId, offset, limit));
    }
}
