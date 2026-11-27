package com.github.evseevda.domainlogicmodule.registration.repository;

import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificate;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateStatus;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersion;
import com.github.evseevda.domainlogicmodule.registration.certificate.domain.RegistrationCertificateVersionStatus;
import com.github.evseevda.domainlogicmodule.transporter.domain.*;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Сохраняет исторические снимки перевозчика, РУ и лицензий. */
class RegistrationSnapshotRepositoryCustomImpl extends JpaQueries implements RegistrationSnapshotRepositoryCustom {
    /** Подключает EntityManager для операций со снимками. */
    RegistrationSnapshotRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Проверяет наличие вида транспорта. */
    @Override
    public boolean transportKindExists(UUID kind) {
        return em.find(TransportKind.class, kind) != null;
    }

    /** Возвращает последнюю версию перевозчика. */
    @Override
    public Optional<UUID> findCurrentVersion(UUID transporter) {
        return one(select("""
                select v.id from TransporterVersion v where v.transporter.id = :id order by v.versionNumber desc
                """, UUID.class).setParameter("id", transporter).setMaxResults(1));
    }

    /** Возвращает вид транспорта указанного снимка. */
    @Override
    public UUID versionTransportKind(UUID source) {
        return ref(TransporterVersion.class, source).getTransportKind().getId();
    }

    /** Создаёт активного перевозчика для подключённой организации. */
    @Override
    public void insertTransporter(UUID id, UUID organization, UUID kind) {
        em.persist(new Transporter(id, ref(TransporterOrganization.class, organization),
                ref(TransportKind.class, kind), TransporterStatus.REGISTERED, now()));
    }

    /** Блокирует перевозчика для последовательной нумерации версий. */
    @Override
    public void lockTransporter(UUID id) {
        em.find(Transporter.class, id, LockModeType.PESSIMISTIC_WRITE);
    }

    /** Обновляет текущее состояние перевозчика. */
    @Override
    public void updateTransporter(UUID kind, int status, UUID id) {
        ref(Transporter.class, id).revise(ref(TransportKind.class, kind), TransporterStatus.fromId(status));
    }

    /** Добавляет новую версию перевозчика. */
    @Override
    public void insertVersion(UUID id, UUID version, UUID kind, int status) {
        int next = select("""
                select coalesce(max(v.versionNumber), 0) from TransporterVersion v where v.transporter.id = :id
                """, Integer.class).setParameter("id", id).getSingleResult() + 1;
        em.persist(new TransporterVersion(version, ref(Transporter.class, id), next,
                ref(TransportKind.class, kind), TransporterStatus.fromId(status), now()));
    }

    /** Копирует лицензии в новый снимок с выделением новых идентификаторов. */
    @Override
    public void copyVersionLicenses(UUID version, UUID source) {
        var snapshot = ref(TransporterVersion.class, version);
        var licenses = select("select l from TransporterLicense l where l.transporterVersion.id = :id", TransporterLicense.class)
                .setParameter("id", source).getResultList();
        for (var license : licenses) {
            em.persist(new TransporterLicense(UUID.randomUUID(), license.getTransporter(), snapshot, license.getLicenseNumber(),
                    license.getIssuedAt(), license.getExpiresAt(), license.getIssuingAuthority(), license.getStatus()));
        }
    }

    /** Сохраняет лицензию снимка, сохраняя состояние неизменившейся лицензии. */
    @Override
    public void insertLicense(UUID license, UUID id, UUID version, UUID source, String number, LocalDate issued,
                              LocalDate expires, String authority) {
        var status = source == null ? TransporterLicenseStatus.ACTIVE : one(select("""
                select l.status from TransporterLicense l where l.transporterVersion.id = :source and l.licenseNumber = :number
                    and l.issuedAt = :issued and l.expiresAt = :expires and l.issuingAuthority = :authority order by l.id
                """, TransporterLicenseStatus.class).setParameter("source", source).setParameter("number", number)
                .setParameter("issued", issued).setParameter("expires", expires).setParameter("authority", authority)
                .setMaxResults(1)).orElse(TransporterLicenseStatus.ACTIVE);
        em.persist(new TransporterLicense(license, ref(Transporter.class, id), ref(TransporterVersion.class, version),
                number, issued, expires, authority, status));
    }

    /** Создаёт постоянную запись РУ. */
    @Override
    public void insertCertificate(UUID certificate, UUID transporter, String number) {
        em.persist(new RegistrationCertificate(certificate, ref(Transporter.class, transporter), number,
                RegistrationCertificateStatus.ACTIVE, now()));
    }

    /** Блокирует РУ перед изменением его истории. */
    @Override
    public UUID lockCertificate(UUID transporter) {
        return select("select c from RegistrationCertificate c where c.transporter.id = :id", RegistrationCertificate.class)
                .setParameter("id", transporter).setLockMode(LockModeType.PESSIMISTIC_WRITE).getSingleResult().getId();
    }

    /** Возвращает идентификатор последней версии РУ. */
    @Override
    public UUID latestCertificateVersion(UUID certificate) {
        return select("""
                select v.id from RegistrationCertificateVersion v where v.certificate.id = :id order by v.versionNumber desc
                """, UUID.class).setParameter("id", certificate).setMaxResults(1).getSingleResult();
    }

    /** Архивирует или аннулирует предыдущую версию РУ. */
    @Override
    public void updateCertificateVersionStatus(UUID previous, int status) {
        ref(RegistrationCertificateVersion.class, previous).changeStatus(RegistrationCertificateVersionStatus.fromId(status));
    }

    /** Отмечает удостоверение аннулированным. */
    @Override
    public void revokeCertificate(UUID certificate) {
        ref(RegistrationCertificate.class, certificate).revoke();
    }

    /** Добавляет версию РУ со ссылкой на новый снимок перевозчика. */
    @Override
    public void insertCertificateVersion(UUID id, UUID certificate, UUID version, UUID previous, String description) {
        var prior = ref(RegistrationCertificateVersion.class, previous);
        int number = prior == null ? 1 : prior.getVersionNumber() + 1;
        em.persist(new RegistrationCertificateVersion(id, ref(RegistrationCertificate.class, certificate),
                ref(TransporterVersion.class, version), number, RegistrationCertificateVersionStatus.CURRENT,
                prior, description, now()));
    }
}
