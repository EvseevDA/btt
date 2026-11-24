package com.github.evseevda.domainlogicmodule.transporter.conversion;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TransporterLicenseStatusConverter implements AttributeConverter<TransporterLicenseStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(TransporterLicenseStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public TransporterLicenseStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterLicenseStatus.fromId(id);
    }
}
