package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogic.model.RegistrationCertificateDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateVersionDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import com.github.evseevda.domainlogic.model.TransporterVersionDto;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.Request;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface RegistrationHistoryRepositoryCustom {
    /** Читает страницу заявлений с проверкой связи с пользователем или организацией. */
    List<Request> visibleRequests(UUID actor, List<Integer> types, UUID transporter, Integer type, Integer status, int limit, int offset);

    /** Возвращает РУ и идентификатор его последней версии. */
    Optional<RegistrationCertificateDto> findCertificate(UUID transporter);

    /** Возвращает страницу версий РУ от новой к старой. */
    List<RegistrationCertificateVersionDto> certificateVersions(UUID certificate, int limit, int offset);

    /** Возвращает конкретную версию в пределах указанного РУ. */
    Optional<RegistrationCertificateVersionDto> findCertificateVersion(UUID certificate, UUID version);

    /** Читает конкретный снимок перевозчика. */
    Optional<TransporterVersionDto> findTransporterVersion(UUID transporter, UUID version);

    /** Читает лицензии конкретного снимка перевозчика. */
    List<TransporterLicenseDto> licenses(UUID version, UUID transporter);

    /** Возвращает перевозчика, которому принадлежит РУ. */
    Optional<UUID> findCertificateTransporter(UUID certificate);

    /** Возвращает организацию перевозчика. */
    Optional<UUID> findOrganization(UUID transporter);

    /** Проверяет членство пользователя для доступа к истории. */
    boolean isOrganizationMember(UUID organization, UUID actor);

    /** Проверяет право представлять организацию при чтении её заявлений. */
    boolean mayRepresent(UUID organization, UUID actor);
}
