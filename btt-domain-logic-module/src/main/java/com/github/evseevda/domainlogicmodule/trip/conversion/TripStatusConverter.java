package com.github.evseevda.domainlogicmodule.trip.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.trip.domain.TripStatus;
import jakarta.persistence.Converter;

/** Преобразует TripStatus в числовой код для хранения через JPA. */
@Converter
public class TripStatusConverter extends IntegerIdEnumConverter<TripStatus> {

    /** Настраивает преобразование кодов TripStatus. */
    public TripStatusConverter() {
        super(TripStatus::getId, TripStatus::fromId);
    }
}
