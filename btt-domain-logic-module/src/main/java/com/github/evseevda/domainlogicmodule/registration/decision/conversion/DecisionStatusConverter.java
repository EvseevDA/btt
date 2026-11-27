package com.github.evseevda.domainlogicmodule.registration.decision.conversion;

import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует DecisionStatus в числовой код для хранения через JPA. */
@Converter
public class DecisionStatusConverter implements AttributeConverter<DecisionStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(DecisionStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public DecisionStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : DecisionStatus.fromId(id);
    }
}
