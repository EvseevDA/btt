package com.github.evseevda.domainlogicmodule.organization.conversion;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class OrganizationRequestStatusConverter implements AttributeConverter<OrganizationRequestStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(OrganizationRequestStatus type) {
        return type == null ? null : type.getId();
    }

    @Override
    public OrganizationRequestStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : OrganizationRequestStatus.fromId(id);
    }
}
