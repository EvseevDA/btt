package com.github.evseevda.domainlogicmodule.transporter.service;

import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseEntity;

/** Преобразует лицензии перевозчика в DTO. */
public final class TransporterLicenseMapper {
    private TransporterLicenseMapper() {}

    /** Преобразует сохранённую лицензию в проекцию каталога или истории. */
    public static TransporterLicenseDto toDto(TransporterLicenseEntity license) {
        return new TransporterLicenseDto(
                license.getId(),
                license.getTransporter().getId(),
                license.getLicenseNumber(),
                license.getIssuedAt(),
                license.getExpiresAt(),
                license.getIssuingAuthority(),
                license.getStatus().getId());
    }
}
