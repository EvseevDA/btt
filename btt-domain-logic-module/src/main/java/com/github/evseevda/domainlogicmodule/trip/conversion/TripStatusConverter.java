package com.github.evseevda.domainlogicmodule.trip.conversion;

import com.github.evseevda.domainlogicmodule.trip.domain.TripStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class TripStatusConverter implements AttributeConverter<TripStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(TripStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public TripStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : TripStatus.fromId(id);
    }
}
