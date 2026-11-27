package com.github.evseevda.domainlogicmodule.ticket.resource;

import com.github.evseevda.domainlogic.api.TicketsApi;
import com.github.evseevda.domainlogic.model.BookTicket;
import com.github.evseevda.domainlogic.model.TicketDto;

import com.github.evseevda.domainlogicmodule.ticket.service.TicketService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TicketsController implements TicketsApi {
    private final ObjectProvider<TicketService> services;

    public TicketsController(ObjectProvider<TicketService> services) {
        this.services = services;
    }


    @Override
    public ResponseEntity<TicketDto> bookTicket(BookTicket bookTicket) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.book(bookTicket));
    }

    @Override
    public ResponseEntity<TicketDto> cancelTicket(UUID ticketId) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.cancel(ticketId));
    }

    @Override
    public ResponseEntity<TicketDto> getTicket(UUID id) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<List<TicketDto>> listTickets(UUID tripId, Integer offset, Integer limit) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.list(tripId, offset, limit));
    }
}
