package com.github.evseevda.domainlogicmodule.registration.request.conversion;

import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TransporterRequestStatusConverter implements AttributeConverter<TransporterRequestStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(TransporterRequestStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public TransporterRequestStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterRequestStatus.fromId(id);
    }
}
