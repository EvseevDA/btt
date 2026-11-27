package com.github.evseevda.domainlogicmodule.registration.service;

import com.github.evseevda.domainlogic.model.TransporterSnapshotInput;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationSnapshotRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.HashSet;
import java.util.UUID;

@Component
@Profile("!mock-api")
class RegistrationSnapshots {
    private final RegistrationSnapshotRepository repository;
    private final ObjectMapper json;

    RegistrationSnapshots(RegistrationSnapshotRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    String validateAndSerialize(TransporterSnapshotInput data) {
        if (data == null || data.getTransportKindId() == null || data.getLicenses() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите данные перевозчика и лицензии");
        }
        if (!repository.transportKindExists(data.getTransportKindId())) {
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
        return repository.findCurrentVersion(transporter).orElseThrow(
                () -> new ResponseStatusException(HttpStatus.CONFLICT, "У перевозчика нет версии"));
    }

    UUID apply(UUID organization, UUID transporter, UUID source, int type, String proposed, String description) {
        boolean initial = type == 1;
        boolean cancellation = type == 3 || type == 4;
        UUID id = initial ? UUID.randomUUID() : transporter;
        var data = cancellation ? null : json.readValue(proposed, TransporterSnapshotInput.class);
        UUID kind = cancellation
                ? repository.versionTransportKind(source)
                : data.getTransportKindId();
        int status = cancellation ? 2 : 1;
        if (initial) {
            repository.insertTransporter(id, organization, kind);
        } else {
            repository.lockTransporter(id);
            if (!currentVersion(id).equals(source)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Исходная версия перевозчика изменилась");
            }
            repository.updateTransporter(kind, status, id);
        }
        UUID version = UUID.randomUUID();
        repository.insertVersion(id, version, kind, status);
        if (cancellation) {
            repository.copyVersionLicenses(version, source);
        } else {
            for (var license : data.getLicenses()) {
                repository.insertLicense(UUID.randomUUID(), id, version, source, license.getLicenseNumber().strip(), license.getIssuedAt(), license.getExpiresAt(), license.getIssuingAuthority().strip());
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
            repository.insertCertificate(certificate, transporter, "BTT-" + certificate);
        } else {
            certificate = repository.lockCertificate(transporter);
            previous = repository.latestCertificateVersion(certificate);
            repository.updateCertificateVersionStatus(previous, cancellation ? 3 : 2);
            if (cancellation) {
                repository.revokeCertificate(certificate);
            }
        }
        repository.insertCertificateVersion(UUID.randomUUID(), certificate, version, previous, description);
    }
}
