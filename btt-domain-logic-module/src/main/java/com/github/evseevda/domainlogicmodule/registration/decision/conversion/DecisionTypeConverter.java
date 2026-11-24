package com.github.evseevda.domainlogicmodule.registration.decision.conversion;

import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DecisionTypeConverter implements AttributeConverter<DecisionType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(DecisionType type) {
        return type == null ? null : type.getId();
    }

    @Override
    public DecisionType convertToEntityAttribute(Integer id) {
        return id == null ? null : DecisionType.fromId(id);
    }
}
