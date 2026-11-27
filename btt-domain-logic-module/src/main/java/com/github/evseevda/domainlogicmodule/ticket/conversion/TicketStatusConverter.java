package com.github.evseevda.domainlogicmodule.ticket.conversion;

import com.github.evseevda.domainlogicmodule.ticket.domain.TicketStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует TicketStatus в числовой код для хранения через JPA. */
@Converter
public class TicketStatusConverter implements AttributeConverter<TicketStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(TicketStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public TicketStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TicketStatus.fromId(id);
    }
}
