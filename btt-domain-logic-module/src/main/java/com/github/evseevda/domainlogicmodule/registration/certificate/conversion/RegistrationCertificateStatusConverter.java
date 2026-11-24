package com.github.evseevda.domainlogicmodule.registration.certificate.conversion;

import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RegistrationCertificateStatusConverter implements AttributeConverter<RegistrationCertificateStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(RegistrationCertificateStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public RegistrationCertificateStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : RegistrationCertificateStatus.fromId(id);
    }
}
