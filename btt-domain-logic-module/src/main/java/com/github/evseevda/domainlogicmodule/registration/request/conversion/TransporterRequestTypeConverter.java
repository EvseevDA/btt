package com.github.evseevda.domainlogicmodule.registration.request.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import jakarta.persistence.Converter;

/** Преобразует TransporterRequestType в числовой код для хранения через JPA. */
@Converter
public class TransporterRequestTypeConverter
        extends IntegerIdEnumConverter<TransporterRequestType> {

    /** Настраивает преобразование кодов TransporterRequestType. */
    public TransporterRequestTypeConverter() {
        super(TransporterRequestType::getId, TransporterRequestType::fromId);
    }
}
