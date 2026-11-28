package com.github.evseevda.domainlogicmodule.transporter.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;
import jakarta.persistence.Converter;

/** Преобразует TransporterStatus в числовой код для хранения через JPA. */
@Converter
public class TransporterStatusConverter extends IntegerIdEnumConverter<TransporterStatus> {

    /** Настраивает преобразование кодов TransporterStatus. */
    public TransporterStatusConverter() {
        super(TransporterStatus::getId, TransporterStatus::fromId);
    }
}
