package com.github.evseevda.domainlogicmodule.transporter.conversion;

import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TransporterStatusConverter implements AttributeConverter<TransporterStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(TransporterStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public TransporterStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TransporterStatus.fromId(id);
    }
}
