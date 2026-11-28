package com.github.evseevda.domainlogicmodule.registration.certificate.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateStatus;
import jakarta.persistence.Converter;

/** Преобразует RegistrationCertificateStatus в числовой код для хранения через JPA. */
@Converter
public class RegistrationCertificateStatusConverter
        extends IntegerIdEnumConverter<RegistrationCertificateStatus> {

    /** Настраивает преобразование кодов RegistrationCertificateStatus. */
    public RegistrationCertificateStatusConverter() {
        super(RegistrationCertificateStatus::getId, RegistrationCertificateStatus::fromId);
    }
}
