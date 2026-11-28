package com.github.evseevda.domainlogicmodule.ticket.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;
import jakarta.persistence.Converter;

/** Преобразует TicketStatus в числовой код для хранения через JPA. */
@Converter
public class TicketStatusConverter extends IntegerIdEnumConverter<TicketStatus> {

    /** Настраивает преобразование кодов TicketStatus. */
    public TicketStatusConverter() {
        super(TicketStatus::getId, TicketStatus::fromId);
    }
}
