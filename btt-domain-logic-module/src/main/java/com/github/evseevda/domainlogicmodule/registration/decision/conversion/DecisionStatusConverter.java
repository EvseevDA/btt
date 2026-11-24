package com.github.evseevda.domainlogicmodule.registration.decision.conversion;

import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class DecisionStatusConverter implements AttributeConverter<DecisionStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(DecisionStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public DecisionStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : DecisionStatus.fromId(id);
    }
}
