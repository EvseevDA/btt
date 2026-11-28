package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateEntity;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateStatus;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionEntity;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionStatus;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransportKindEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterLicenseStatus;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganizationEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterStatus;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterVersionEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Сохраняет исторические снимки перевозчика, РУ и лицензий. */
class RegistrationSnapshotRepositoryCustomImpl extends JpaQueries
        implements RegistrationSnapshotRepositoryCustom {
    /** Подключает EntityManager для операций со снимками. */
    RegistrationSnapshotRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Проверяет наличие вида транспорта. */
    @Override
    public boolean existsTransportKindById(UUID kind) {
        return em.find(TransportKindEntity.class, kind) != null;
    }

    /** Возвращает последнюю версию перевозчика. */
    @Override
    public Optional<UUID> findCurrentVersionIdByTransporterId(UUID transporter) {
        return findSingleResult(
                createTypedQuery(
                                """
                select v.id from TransporterVersionEntity v where v.transporter.id = :id order by v.versionNumber desc
                """,
                                UUID.class)
                        .setParameter("id", transporter)
                        .setMaxResults(1));
    }

    /** Возвращает вид транспорта указанного снимка. */
    @Override
    public UUID getTransportKindIdByVersionId(UUID source) {
        return getReference(TransporterVersionEntity.class, source).getTransportKind().getId();
    }

    /** Создаёт активного перевозчика для подключённой организации. */
    @Override
    public void insertTransporter(UUID id, UUID organization, UUID kind) {
        em.persist(
                new TransporterEntity(
                        id,
                        getReference(TransporterOrganizationEntity.class, organization),
                        getReference(TransportKindEntity.class, kind),
                        TransporterStatus.REGISTERED,
                        getCurrentUtcDateTime()));
    }

    /** Блокирует перевозчика для последовательной нумерации версий. */
    @Override
    public void lockTransporterById(UUID id) {
        em.find(TransporterEntity.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Обновляет текущее состояние перевозчика. */
    @Override
    public void updateTransporter(UUID kind, int status, UUID id) {
        getReference(TransporterEntity.class, id)
                .revise(
                        getReference(TransportKindEntity.class, kind),
                        TransporterStatus.fromId(status));
    }

    /** Добавляет новую версию перевозчика. */
    @Override
    public void insertVersion(UUID id, UUID version, UUID kind, int status) {
        int next =
                createTypedQuery(
                                        """
                select coalesce(max(v.versionNumber), 0) from TransporterVersionEntity v where v.transporter.id = :id
                """,
                                        Integer.class)
                                .setParameter("id", id)
                                .getSingleResult()
                        + 1;
        em.persist(
                new TransporterVersionEntity(
                        version,
                        getReference(TransporterEntity.class, id),
                        next,
                        getReference(TransportKindEntity.class, kind),
                        TransporterStatus.fromId(status),
                        getCurrentUtcDateTime()));
    }

    /** Копирует лицензии в новый снимок с выделением новых идентификаторов. */
    @Override
    public void copyVersionLicenses(UUID version, UUID source) {
        var snapshot = getReference(TransporterVersionEntity.class, version);
        var licenses =
                createTypedQuery(
                                "select l from TransporterLicenseEntity l where l.transporterVersion.id = :id",
                                TransporterLicenseEntity.class)
                        .setParameter("id", source)
                        .getResultList();
        for (var license : licenses) {
            em.persist(
                    new TransporterLicenseEntity(
                            UUID.randomUUID(),
                            license.getTransporter(),
                            snapshot,
                            license.getLicenseNumber(),
                            license.getIssuedAt(),
                            license.getExpiresAt(),
                            license.getIssuingAuthority(),
                            license.getStatus()));
        }
    }

    /** Сохраняет лицензию снимка, сохраняя состояние неизменившейся лицензии. */
    @Override
    public void insertLicense(
            UUID license,
            UUID id,
            UUID version,
            UUID source,
            String number,
            LocalDate issued,
            LocalDate expires,
            String authority) {
        var status =
                source == null
                        ? TransporterLicenseStatus.ACTIVE
                        : findSingleResult(
                                        createTypedQuery(
                                                        """
                select l.status from TransporterLicenseEntity l where l.transporterVersion.id = :source and l.licenseNumber = :number
                    and l.issuedAt = :issued and l.expiresAt = :expires and l.issuingAuthority = :authority order by l.id
                """,
                                                        TransporterLicenseStatus.class)
                                                .setParameter("source", source)
                                                .setParameter("number", number)
                                                .setParameter("issued", issued)
                                                .setParameter("expires", expires)
                                                .setParameter("authority", authority)
                                                .setMaxResults(1))
                                .orElse(TransporterLicenseStatus.ACTIVE);
        em.persist(
                new TransporterLicenseEntity(
                        license,
                        getReference(TransporterEntity.class, id),
                        getReference(TransporterVersionEntity.class, version),
                        number,
                        issued,
                        expires,
                        authority,
                        status));
    }

    /** Создаёт постоянную запись РУ. */
    @Override
    public void insertCertificate(UUID certificate, UUID transporter, String number) {
        em.persist(
                new RegistrationCertificateEntity(
                        certificate,
                        getReference(TransporterEntity.class, transporter),
                        number,
                        RegistrationCertificateStatus.ACTIVE,
                        getCurrentUtcDateTime()));
    }

    /** Блокирует РУ перед изменением его истории. */
    @Override
    public UUID getCertificateIdByTransporterIdWithLock(UUID transporter) {
        return createTypedQuery(
                        "select c from RegistrationCertificateEntity c where c.transporter.id = :id",
                        RegistrationCertificateEntity.class)
                .setParameter("id", transporter)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getSingleResult()
                .getId();
    }

    /** Возвращает идентификатор последней версии РУ. */
    @Override
    public UUID getLatestCertificateVersionIdByCertificateId(UUID certificate) {
        return createTypedQuery(
                        """
                select v.id from RegistrationCertificateVersionEntity v where v.certificate.id = :id order by v.versionNumber desc
                """,
                        UUID.class)
                .setParameter("id", certificate)
                .setMaxResults(1)
                .getSingleResult();
    }

    /** Архивирует или аннулирует предыдущую версию РУ. */
    @Override
    public void updateCertificateVersionStatus(UUID previous, int status) {
        getReference(RegistrationCertificateVersionEntity.class, previous)
                .changeStatus(RegistrationCertificateVersionStatus.fromId(status));
    }

    /** Отмечает удостоверение аннулированным. */
    @Override
    public void revokeCertificate(UUID certificate) {
        getReference(RegistrationCertificateEntity.class, certificate).revoke();
    }

    /** Добавляет версию РУ со ссылкой на новый снимок перевозчика. */
    @Override
    public void insertCertificateVersion(
            UUID id, UUID certificate, UUID version, UUID previous, String description) {
        var prior = getReference(RegistrationCertificateVersionEntity.class, previous);
        int number = prior == null ? 1 : prior.getVersionNumber() + 1;
        em.persist(
                new RegistrationCertificateVersionEntity(
                        id,
                        getReference(RegistrationCertificateEntity.class, certificate),
                        getReference(TransporterVersionEntity.class, version),
                        number,
                        RegistrationCertificateVersionStatus.CURRENT,
                        prior,
                        description,
                        getCurrentUtcDateTime()));
    }
}
