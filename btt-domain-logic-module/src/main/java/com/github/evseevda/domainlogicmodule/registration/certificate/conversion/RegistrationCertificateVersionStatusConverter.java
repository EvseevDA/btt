package com.github.evseevda.domainlogicmodule.registration.certificate.conversion;

import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class RegistrationCertificateVersionStatusConverter implements AttributeConverter<RegistrationCertificateVersionStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(RegistrationCertificateVersionStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public RegistrationCertificateVersionStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : RegistrationCertificateVersionStatus.fromId(id);
    }
}
