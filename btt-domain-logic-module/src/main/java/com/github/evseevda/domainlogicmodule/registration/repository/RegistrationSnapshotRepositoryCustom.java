package com.github.evseevda.domainlogicmodule.registration.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface RegistrationSnapshotRepositoryCustom {
    /** Проверяет наличие вида транспорта. */
    boolean existsTransportKindById(UUID kind);

    /** Возвращает последнюю версию перевозчика. */
    Optional<UUID> findCurrentVersionIdByTransporterId(UUID transporter);

    /** Возвращает вид транспорта указанного снимка. */
    UUID getTransportKindIdByVersionId(UUID source);

    /** Создаёт активного перевозчика для подключённой организации. */
    void insertTransporter(UUID id, UUID organization, UUID kind);

    /** Блокирует перевозчика для последовательной нумерации версий. */
    void lockTransporterById(UUID id);

    /** Обновляет текущее состояние перевозчика. */
    void updateTransporter(UUID kind, int status, UUID id);

    /** Добавляет новую версию перевозчика. */
    void insertVersion(UUID id, UUID version, UUID kind, int status);

    /** Копирует лицензии в новый снимок с выделением новых идентификаторов. */
    void copyVersionLicenses(UUID version, UUID source);

    /** Сохраняет лицензию снимка, сохраняя состояние неизменившейся лицензии. */
    void insertLicense(
            UUID license,
            UUID id,
            UUID version,
            UUID source,
            String number,
            LocalDate issued,
            LocalDate expires,
            String authority);

    /** Создаёт постоянную запись РУ. */
    void insertCertificate(UUID certificate, UUID transporter, String number);

    /** Блокирует РУ перед изменением его истории. */
    UUID getCertificateIdByTransporterIdWithLock(UUID transporter);

    /** Возвращает идентификатор последней версии РУ. */
    UUID getLatestCertificateVersionIdByCertificateId(UUID certificate);

    /** Архивирует или аннулирует предыдущую версию РУ. */
    void updateCertificateVersionStatus(UUID previous, int status);

    /** Отмечает удостоверение аннулированным. */
    void revokeCertificate(UUID certificate);

    /** Добавляет версию РУ со ссылкой на новый снимок перевозчика. */
    void insertCertificateVersion(
            UUID id, UUID certificate, UUID version, UUID previous, String description);
}
