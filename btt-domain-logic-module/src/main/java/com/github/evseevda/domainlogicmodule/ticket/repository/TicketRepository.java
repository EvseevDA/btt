package com.github.evseevda.domainlogicmodule.ticket.repository;

import com.github.evseevda.domainlogicmodule.ticket.domain.Ticket;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Управляет доступом к билетам и блокировками инвентаря рейса. */
public interface TicketRepository extends JpaRepository<Ticket, UUID>, TicketRepositoryCustom {
    /** Тариф и возможность брони для рейса, заблокированного на чтение. */
    record Fare(BigDecimal price, boolean bookable) {
    }
}
