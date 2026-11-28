package com.github.evseevda.domainlogicmodule.registration.certificate.conversion;

import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionStatus;
import jakarta.persistence.Converter;

/** Преобразует RegistrationCertificateVersionStatus в числовой код для хранения через JPA. */
@Converter
public class RegistrationCertificateVersionStatusConverter
        extends IntegerIdEnumConverter<RegistrationCertificateVersionStatus> {

    /** Настраивает преобразование кодов RegistrationCertificateVersionStatus. */
    public RegistrationCertificateVersionStatusConverter() {
        super(
                RegistrationCertificateVersionStatus::getId,
                RegistrationCertificateVersionStatus::fromId);
    }
}
