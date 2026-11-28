package com.github.evseevda.domainlogicmodule.registration.decision.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.registration.decision.domain.DecisionStatus;
import jakarta.persistence.Converter;

/** Преобразует DecisionStatus в числовой код для хранения через JPA. */
@Converter
public class DecisionStatusConverter extends IntegerIdEnumConverter<DecisionStatus> {

    /** Настраивает преобразование кодов DecisionStatus. */
    public DecisionStatusConverter() {
        super(DecisionStatus::getId, DecisionStatus::fromId);
    }
}
