package com.github.evseevda.domainlogicmodule.organization.conversion;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestStatus;
import com.github.evseevda.domainlogicmodule.persistence.IntegerIdEnumConverter;
import jakarta.persistence.Converter;

/** Преобразует OrganizationRequestStatus в числовой код для хранения через JPA. */
@Converter
public class OrganizationRequestStatusConverter
        extends IntegerIdEnumConverter<OrganizationRequestStatus> {

    /** Настраивает преобразование кодов OrganizationRequestStatus. */
    public OrganizationRequestStatusConverter() {
        super(OrganizationRequestStatus::getId, OrganizationRequestStatus::fromId);
    }
}
