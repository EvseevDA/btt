package com.github.evseevda.domainlogicmodule.registration.request.conversion;

import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует TransporterRequestType в числовой код для хранения через JPA. */
@Converter
public class TransporterRequestTypeConverter implements AttributeConverter<TransporterRequestType, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(TransporterRequestType type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public TransporterRequestType convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterRequestType.fromId(id);
    }
}
