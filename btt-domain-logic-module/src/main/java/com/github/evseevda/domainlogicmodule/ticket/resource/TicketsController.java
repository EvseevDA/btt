package com.github.evseevda.domainlogicmodule.ticket.resource;

import com.github.evseevda.domainlogic.api.TicketsApi;
import com.github.evseevda.domainlogic.model.BookTicket;
import com.github.evseevda.domainlogic.model.TicketDto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class TicketsController implements TicketsApi {

    @Override
    public ResponseEntity<TicketDto> bookTicket(BookTicket bookTicket) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TicketDto> cancelTicket(UUID ticketId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TicketDto> getTicket(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<TicketDto>> listTickets(UUID tripId, Integer offset, Integer limit) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
