package com.github.evseevda.domainlogicmodule.registration.request.conversion;

import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует TransporterRequestStatus в числовой код для хранения через JPA. */
@Converter
public class TransporterRequestStatusConverter implements AttributeConverter<TransporterRequestStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(TransporterRequestStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public TransporterRequestStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterRequestStatus.fromId(id);
    }
}
