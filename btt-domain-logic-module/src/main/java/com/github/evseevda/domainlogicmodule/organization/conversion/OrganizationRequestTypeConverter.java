package com.github.evseevda.domainlogicmodule.organization.conversion;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OrganizationRequestTypeConverter implements AttributeConverter<OrganizationRequestType, Integer> {
    @Override
    public Integer convertToDatabaseColumn(OrganizationRequestType type) {
        return type == null ? null : type.getId();
    }

    @Override
    public OrganizationRequestType convertToEntityAttribute(Integer id) {
        return id == null ? null : OrganizationRequestType.fromId(id);
    }
}
