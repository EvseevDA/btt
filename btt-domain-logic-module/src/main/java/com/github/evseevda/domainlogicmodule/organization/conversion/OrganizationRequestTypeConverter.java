package com.github.evseevda.domainlogicmodule.organization.conversion;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestType;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/** Преобразует OrganizationRequestType в числовой код для хранения через JPA. */
@Converter
public class OrganizationRequestTypeConverter implements AttributeConverter<OrganizationRequestType, Integer> {
    /** Преобразует значение enum в его числовой код, сохраняя null. */
    @Override
    public Integer convertToDatabaseColumn(OrganizationRequestType type) {
        return type == null ? null : type.getId();
    }

    /** Восстанавливает enum по коду, сохраняя null. Неизвестный код вызывает исключение. */
    @Override
    public OrganizationRequestType convertToEntityAttribute(Integer id) {
        return id == null ? null : OrganizationRequestType.fromId(id);
    }
}
