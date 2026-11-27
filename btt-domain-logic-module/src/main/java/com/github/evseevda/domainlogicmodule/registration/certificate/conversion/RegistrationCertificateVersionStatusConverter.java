package com.github.evseevda.domainlogicmodule.registration.certificate.conversion;

import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует RegistrationCertificateVersionStatus в числовой код для хранения через JPA. */
@Converter
public class RegistrationCertificateVersionStatusConverter implements AttributeConverter<RegistrationCertificateVersionStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(RegistrationCertificateVersionStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public RegistrationCertificateVersionStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : RegistrationCertificateVersionStatus.fromId(id);
    }
}
