package com.github.evseevda.domainlogicmodule.registration.decision.conversion;

import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует DecisionType в числовой код для хранения через JPA. */
@Converter
public class DecisionTypeConverter implements AttributeConverter<DecisionType, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(DecisionType type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public DecisionType convertToEntityAttribute(Integer id) {
        return id == null ? null : DecisionType.fromId(id);
    }
}
