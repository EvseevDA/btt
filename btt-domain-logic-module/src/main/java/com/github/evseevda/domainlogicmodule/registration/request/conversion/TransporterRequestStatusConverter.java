package com.github.evseevda.domainlogicmodule.registration.request.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestStatus;
import jakarta.persistence.Converter;

/** Преобразует TransporterRequestStatus в числовой код для хранения через JPA. */
@Converter
public class TransporterRequestStatusConverter
        extends IntegerIdEnumConverter<TransporterRequestStatus> {

    /** Настраивает преобразование кодов TransporterRequestStatus. */
    public TransporterRequestStatusConverter() {
        super(TransporterRequestStatus::getId, TransporterRequestStatus::fromId);
    }
}
