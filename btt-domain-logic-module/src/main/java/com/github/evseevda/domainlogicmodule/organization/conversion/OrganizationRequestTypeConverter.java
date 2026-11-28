package com.github.evseevda.domainlogicmodule.organization.conversion;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestType;
import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import jakarta.persistence.Converter;

/** Преобразует OrganizationRequestType в числовой код для хранения через JPA. */
@Converter
public class OrganizationRequestTypeConverter
        extends IntegerIdEnumConverter<OrganizationRequestType> {

    /** Настраивает преобразование кодов OrganizationRequestType. */
    public OrganizationRequestTypeConverter() {
        super(OrganizationRequestType::getId, OrganizationRequestType::fromId);
    }
}
