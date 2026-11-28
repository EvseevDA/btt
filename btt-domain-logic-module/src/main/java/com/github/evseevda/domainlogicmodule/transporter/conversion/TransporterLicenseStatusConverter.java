package com.github.evseevda.domainlogicmodule.transporter.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseStatus;
import jakarta.persistence.Converter;

/** Преобразует TransporterLicenseStatus в числовой код для хранения через JPA. */
@Converter
public class TransporterLicenseStatusConverter
        extends IntegerIdEnumConverter<TransporterLicenseStatus> {

    /** Настраивает преобразование кодов TransporterLicenseStatus. */
    public TransporterLicenseStatusConverter() {
        super(TransporterLicenseStatus::getId, TransporterLicenseStatus::fromId);
    }
}
