package com.github.evseevda.domainlogicmodule.registration.request.conversion;

import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TransporterRequestTypeConverter implements AttributeConverter<TransporterRequestType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(TransporterRequestType type) {
        return type == null ? null : type.getId();
    }

    @Override
    public TransporterRequestType convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterRequestType.fromId(id);
    }
}
