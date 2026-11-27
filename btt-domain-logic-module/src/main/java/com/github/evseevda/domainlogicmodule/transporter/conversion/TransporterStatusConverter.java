package com.github.evseevda.domainlogicmodule.transporter.conversion;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует TransporterStatus в числовой код для хранения через JPA. */
@Converter
public class TransporterStatusConverter implements AttributeConverter<TransporterStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(TransporterStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public TransporterStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterStatus.fromId(id);
    }
}
