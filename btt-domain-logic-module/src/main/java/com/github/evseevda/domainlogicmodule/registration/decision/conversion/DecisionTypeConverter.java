package com.github.evseevda.domainlogicmodule.registration.decision.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionType;
import jakarta.persistence.Converter;

/** Преобразует DecisionType в числовой код для хранения через JPA. */
@Converter
public class DecisionTypeConverter extends IntegerIdEnumConverter<DecisionType> {

    /** Настраивает преобразование кодов DecisionType. */
    public DecisionTypeConverter() {
        super(DecisionType::getId, DecisionType::fromId);
    }
}
