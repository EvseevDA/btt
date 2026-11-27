package com.github.evseevda.domainlogicmodule.registration.service;

import com.github.evseevda.domainlogic.model.TransporterSnapshotInput;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.UUID;

@Component
@Profile("!mock-api")
class RegistrationSnapshots {
    private final JdbcClient db;
    private final ObjectMapper json;

    RegistrationSnapshots(JdbcClient db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    String validateAndSerialize(TransporterSnapshotInput data) {
        if (data == null || data.getTransportKindId() == null || data.getLicenses() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите данные перевозчика и лицензии");
        }
        if (db.sql("SELECT count(*) FROM transport_kind WHERE id = :id").param("id", data.getTransportKindId())
                .query(Integer.class).single() == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Неизвестный вид транспорта");
        }
        var licenseNumbers = new HashSet<String>();
        for (var license : data.getLicenses()) {
            if (license == null || license.getLicenseNumber() == null || license.getLicenseNumber().isBlank()
                    || !licenseNumbers.add(license.getLicenseNumber().strip()) || license.getIssuedAt() == null
                    || license.getExpiresAt() == null || license.getExpiresAt().isBefore(license.getIssuedAt())
                    || license.getIssuingAuthority() == null || license.getIssuingAuthority().isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Проверьте реквизиты лицензий");
            }
        }
        return json.writeValueAsString(data);
    }

    UUID currentVersion(UUID transporter) {
        return db.sql("""
                SELECT id FROM transporter_version WHERE transporter_id = :transporter ORDER BY version_number DESC LIMIT 1
                """).param("transporter", transporter).query(UUID.class).optional().orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "У перевозчика нет версии"));
    }

    UUID apply(UUID organization, UUID transporter, UUID source, int type, String proposed, String description) {
        boolean initial = type == 1;
        boolean cancellation = type == 3 || type == 4;
        UUID id = initial ? UUID.randomUUID() : transporter;
        var data = cancellation ? null : json.readValue(proposed, TransporterSnapshotInput.class);
        UUID kind = cancellation
                ? db.sql("SELECT transport_kind_id FROM transporter_version WHERE id = :id").param("id", source)
                    .query(UUID.class).single()
                : data.getTransportKindId();
        int status = cancellation ? 2 : 1;
        if (initial) {
            db.sql("""
                    INSERT INTO transporter (id, organization_id, transport_kind_id, status, created_at)
                    VALUES (:id, :org, :kind, 1, timezone('UTC', clock_timestamp()))
                    """).param("id", id).param("org", organization).param("kind", kind).update();
        } else {
            db.sql("SELECT id FROM transporter WHERE id = :id FOR UPDATE").param("id", id).query(UUID.class).single();
            if (!currentVersion(id).equals(source)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Исходная версия перевозчика изменилась");
            }
            db.sql("UPDATE transporter SET transport_kind_id = :kind, status = :status WHERE id = :id")
                    .param("kind", kind).param("status", status).param("id", id).update();
        }
        UUID version = UUID.randomUUID();
        db.sql("""
                INSERT INTO transporter_version (id, transporter_id, version_number, transport_kind_id, status, created_at)
                SELECT :version, :id, coalesce(max(version_number), 0) + 1, :kind, :status, timezone('UTC', clock_timestamp())
                FROM transporter_version WHERE transporter_id = :id
                """).param("id", id).param("version", version).param("kind", kind).param("status", status).update();
        if (cancellation) {
            db.sql("""
                    INSERT INTO transporter_license (id, transporter_id, transporter_version_id, license_number,
                        issued_at, expires_at, issuing_authority, status)
                    SELECT gen_random_uuid(), transporter_id, :version, license_number,
                        issued_at, expires_at, issuing_authority, status
                    FROM transporter_license WHERE transporter_version_id = :source
                    """).param("version", version).param("source", source).update();
        } else {
            for (var license : data.getLicenses()) {
                db.sql("""
                        INSERT INTO transporter_license (id, transporter_id, transporter_version_id, license_number,
                            issued_at, expires_at, issuing_authority, status)
                        VALUES (:id, :transporter, :version, :number, :issued, :expires, :authority,
                            coalesce((SELECT status FROM transporter_license
                                WHERE transporter_version_id = :source AND license_number = :number
                                  AND issued_at = :issued AND expires_at = :expires AND issuing_authority = :authority
                                ORDER BY id LIMIT 1), 1))
                        """).param("id", UUID.randomUUID()).param("transporter", id).param("version", version)
                        .param("source", source).param("number", license.getLicenseNumber().strip())
                        .param("issued", license.getIssuedAt()).param("expires", license.getExpiresAt())
                        .param("authority", license.getIssuingAuthority().strip()).update();
            }
        }
        certificate(id, version, initial, cancellation, description);
        return id;
    }

    private void certificate(UUID transporter, UUID version, boolean initial, boolean cancellation, String description) {
        UUID certificate;
        UUID previous = null;
        if (initial) {
            certificate = UUID.randomUUID();
            db.sql("""
                    INSERT INTO transporter_registration_certificate (id, transporter_id, certificate_number, status, created_at)
                    VALUES (:id, :transporter, :number, 1, timezone('UTC', clock_timestamp()))
                    """).param("id", certificate).param("transporter", transporter)
                    .param("number", "BTT-" + certificate).update();
        } else {
            certificate = db.sql("""
                    SELECT id FROM transporter_registration_certificate WHERE transporter_id = :transporter FOR UPDATE
                    """).param("transporter", transporter).query(UUID.class).single();
            previous = db.sql("""
                    SELECT id FROM transporter_registration_certificate_version WHERE certificate_id = :id
                    ORDER BY version_number DESC LIMIT 1
                    """).param("id", certificate).query(UUID.class).single();
            db.sql("UPDATE transporter_registration_certificate_version SET status = :status WHERE id = :id")
                    .param("id", previous).param("status", cancellation ? 3 : 2).update();
            if (cancellation) {
                db.sql("UPDATE transporter_registration_certificate SET status = 2 WHERE id = :id")
                        .param("id", certificate).update();
            }
        }
        db.sql("""
                INSERT INTO transporter_registration_certificate_version (id, certificate_id, transporter_version_id,
                    version_number, status, source_version_id, previous_version_id, change_description, created_at)
                SELECT :id, :certificate, :version, coalesce(max(version_number), 0) + 1, 1,
                    :previous, :previous, :description, timezone('UTC', clock_timestamp())
                FROM transporter_registration_certificate_version WHERE certificate_id = :certificate
                """).param("id", UUID.randomUUID()).param("certificate", certificate).param("version", version)
                .param("previous", previous).param("description", description).update();
    }
}
