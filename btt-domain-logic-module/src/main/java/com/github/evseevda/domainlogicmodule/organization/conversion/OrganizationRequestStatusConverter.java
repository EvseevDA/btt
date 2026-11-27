package com.github.evseevda.domainlogicmodule.organization.conversion;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestStatus;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует OrganizationRequestStatus в числовой код для хранения через JPA. */
@Converter
public class OrganizationRequestStatusConverter implements AttributeConverter<OrganizationRequestStatus, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(OrganizationRequestStatus type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public OrganizationRequestStatus convertToEntityAttribute(Integer id) {
        return id == null ? null : OrganizationRequestStatus.fromId(id);
    }
}
