package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogic.model.RegistrationCertificateDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateVersionDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import com.github.evseevda.domainlogic.model.TransporterVersionDto;
import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationMembershipEntity;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateEntity;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionEntity;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.RegistrationRequestProjection;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestEntity;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestStatus;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersionEntity;
import com.github.evseevda.domainlogicmodule.transporter.service.TransporterLicenseMapper;
import jakarta.persistence.EntityManager;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Читает доступные заявления и исторические снимки перевозчика и РУ. */
class RegistrationHistoryRepositoryCustomImpl extends JpaQueries
        implements RegistrationHistoryRepositoryCustom {
    /** Подключает EntityManager для чтения истории. */
    RegistrationHistoryRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Читает страницу заявлений с проверкой связи с пользователем или организацией. */
    @Override
    public List<RegistrationRequestProjection> findAllVisibleRequests(
            UUID actor,
            List<Integer> types,
            UUID transporter,
            Integer type,
            Integer status,
            int limit,
            int offset) {
        TransporterRequestStatus state =
                status == null
                        ? null
                        : Arrays.stream(TransporterRequestStatus.values())
                                .filter(value -> value.getId() == status)
                                .findFirst()
                                .orElse(null);
        if (status != null && state == null) {
            return List.of();
        }
        var filterType = type == null ? null : TransporterRequestType.fromId(type);
        var reviewerTypes =
                types.isEmpty()
                        ? List.of(TransporterRequestType.INITIAL_REGISTRATION)
                        : types.stream().map(TransporterRequestType::fromId).toList();
        return createTypedQuery(
                        """
                select r from TransporterRequestEntity r
                where (r.applicantContact.id = :actor or r.executor.id = :actor or (:reviews = true and r.type in :types)
                    or (r.type <> CANCELLATION_BY_PORTAL_ADMINISTRATION and exists (
                        select m.id from OrganizationMembershipEntity m where m.id.organizationId = r.organization.id
                        and m.id.userId = :actor and m.canRepresent)))
                  and (:transporter is null or r.transporter.id = :transporter)
                  and (:type is null or r.type = :type)
                  and (:state is null or r.status = :state)
                order by r.createdAt desc, r.id
                """,
                        TransporterRequestEntity.class)
                .setParameter("actor", actor)
                .setParameter("reviews", !types.isEmpty())
                .setParameter("types", reviewerTypes)
                .setParameter("transporter", transporter)
                .setParameter("state", state)
                .setParameter("type", filterType)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(RegistrationRequestRepositoryCustomImpl::projection)
                .toList();
    }

    /** Возвращает РУ и идентификатор его последней версии. */
    @Override
    public Optional<RegistrationCertificateDto> findCertificateByTransporterId(UUID transporter) {
        return findSingleResult(
                        createTypedQuery(
                                        "select c from RegistrationCertificateEntity c where c.transporter.id = :id",
                                        RegistrationCertificateEntity.class)
                                .setParameter("id", transporter))
                .map(
                        c -> {
                            var latest =
                                    findSingleResult(
                                            createTypedQuery(
                                                            """
                            select v.id from RegistrationCertificateVersionEntity v where v.certificate.id = :id
                            order by v.versionNumber desc
                            """,
                                                            UUID.class)
                                                    .setParameter("id", c.getId())
                                                    .setMaxResults(1));
                            return new RegistrationCertificateDto(
                                            c.getId(),
                                            transporter,
                                            c.getCertificateNumber(),
                                            c.getStatus().getId(),
                                            c.getCreatedAt()
                                                    .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                                    .currentVersionId(latest.orElse(null));
                        });
    }

    /** Возвращает страницу версий РУ от новой к старой. */
    @Override
    public List<RegistrationCertificateVersionDto> findAllCertificateVersionsByCertificateId(
            UUID certificate, int limit, int offset) {
        return createTypedQuery(
                        """
                select v from RegistrationCertificateVersionEntity v where v.certificate.id = :id order by v.versionNumber desc
                """,
                        RegistrationCertificateVersionEntity.class)
                .setParameter("id", certificate)
                .setFirstResult(offset)
                .setMaxResults(limit)
                .getResultList()
                .stream()
                .map(RegistrationHistoryRepositoryCustomImpl::toDto)
                .toList();
    }

    /** Возвращает конкретную версию в пределах указанного РУ. */
    @Override
    public Optional<RegistrationCertificateVersionDto> findCertificateVersionByCertificateIdAndId(
            UUID certificate, UUID version) {
        return findSingleResult(
                        createTypedQuery(
                                        """
                select v from RegistrationCertificateVersionEntity v where v.certificate.id = :certificate and v.id = :version
                """,
                                        RegistrationCertificateVersionEntity.class)
                                .setParameter("certificate", certificate)
                                .setParameter("version", version))
                .map(RegistrationHistoryRepositoryCustomImpl::toDto);
    }

    /** Читает конкретный снимок перевозчика. */
    @Override
    public Optional<TransporterVersionDto> findTransporterVersionByTransporterIdAndId(
            UUID transporter, UUID version) {
        return findSingleResult(
                        createTypedQuery(
                                        """
                select v from TransporterVersionEntity v where v.transporter.id = :transporter and v.id = :version
                """,
                                        TransporterVersionEntity.class)
                                .setParameter("transporter", transporter)
                                .setParameter("version", version))
                .map(
                        v ->
                                new TransporterVersionDto(
                                        version,
                                        transporter,
                                        v.getVersionNumber(),
                                        v.getTransportKind().getId(),
                                        v.getStatus().getId(),
                                        List.of(),
                                        v.getCreatedAt()
                                                .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)));
    }

    /** Читает лицензии конкретного снимка перевозчика. */
    @Override
    public List<TransporterLicenseDto> findAllLicensesByTransporterVersionId(
            UUID version, UUID transporter) {
        return createTypedQuery(
                        """
                select l from TransporterLicenseEntity l where l.transporterVersion.id = :version and l.transporter.id = :carrier
                order by l.licenseNumber, l.id
                """,
                        TransporterLicenseEntity.class)
                .setParameter("version", version)
                .setParameter("carrier", transporter)
                .getResultList()
                .stream()
                .map(TransporterLicenseMapper::toDto)
                .toList();
    }

    /** Возвращает перевозчика, которому принадлежит РУ. */
    @Override
    public Optional<UUID> findTransporterIdByCertificateId(UUID certificate) {
        return findSingleResult(
                createTypedQuery(
                                "select c.transporter.id from RegistrationCertificateEntity c where c.id = :id",
                                UUID.class)
                        .setParameter("id", certificate));
    }

    /** Возвращает организацию перевозчика. */
    @Override
    public Optional<UUID> findOrganizationIdByTransporterId(UUID transporter) {
        return findSingleResult(
                createTypedQuery(
                                "select c.organization.id from TransporterEntity c where c.id = :id",
                                UUID.class)
                        .setParameter("id", transporter));
    }

    /** Проверяет членство пользователя для доступа к истории. */
    @Override
    public boolean isOrganizationMember(UUID organization, UUID actor) {
        return em.find(
                        OrganizationMembershipEntity.class,
                        new OrganizationMembershipEntity.Key(organization, actor))
                != null;
    }

    /** Проверяет право представлять организацию при чтении её заявлений. */
    @Override
    public boolean mayRepresent(UUID organization, UUID actor) {
        var member =
                em.find(
                        OrganizationMembershipEntity.class,
                        new OrganizationMembershipEntity.Key(organization, actor));
        return member != null && member.isCanRepresent();
    }

    private static RegistrationCertificateVersionDto toDto(
            RegistrationCertificateVersionEntity version) {
        return new RegistrationCertificateVersionDto(
                        version.getId(),
                        version.getCertificate().getId(),
                        version.getVersionNumber(),
                        version.getStatus().getId(),
                        version.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME))
                .transporterVersionId(version.getTransporterVersion().getId())
                .sourceVersionId(
                        version.getSourceVersion() == null
                                ? null
                                : version.getSourceVersion().getId())
                .previousVersionId(
                        version.getPreviousVersion() == null
                                ? null
                                : version.getPreviousVersion().getId())
                .changeDescription(version.getChangeDescription());
    }
}
