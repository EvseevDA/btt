package com.github.evseevda.domainlogicmodule.ticket.conversion;

import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TicketStatusConverter implements AttributeConverter<TicketStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(TicketStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public TicketStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TicketStatus.fromId(id);
    }
}
