package com.github.evseevda.domainlogicmodule.trip.conversion;

import com.github.evseevda.domainlogicmodule.trip.domain.TripStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует TripStatus в числовой код для хранения через JPA. */
@Converter
public class TripStatusConverter implements AttributeConverter<TripStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(TripStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public TripStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TripStatus.fromId(id);
    }
}
