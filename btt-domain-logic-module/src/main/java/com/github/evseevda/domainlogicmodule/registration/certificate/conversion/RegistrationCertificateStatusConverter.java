package com.github.evseevda.domainlogicmodule.registration.certificate.conversion;

import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует RegistrationCertificateStatus в числовой код для хранения через JPA. */
@Converter
public class RegistrationCertificateStatusConverter implements AttributeConverter<RegistrationCertificateStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(RegistrationCertificateStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public RegistrationCertificateStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : RegistrationCertificateStatus.fromId(id);
    }
}
