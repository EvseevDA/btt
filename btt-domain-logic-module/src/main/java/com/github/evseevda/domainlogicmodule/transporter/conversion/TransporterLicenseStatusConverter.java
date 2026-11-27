package com.github.evseevda.domainlogicmodule.transporter.conversion;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует TransporterLicenseStatus в числовой код для хранения через JPA. */
@Converter
public class TransporterLicenseStatusConverter implements AttributeConverter<TransporterLicenseStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(TransporterLicenseStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public TransporterLicenseStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterLicenseStatus.fromId(id);
    }
}
