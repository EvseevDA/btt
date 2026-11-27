package com.github.evseevda.domainlogicmodule.registration.service;

import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateVersionDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogic.model.TransporterSnapshotInput;
import com.github.evseevda.domainlogic.model.TransporterVersionDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!mock-api")
@Transactional(readOnly = true)
public class RegistrationReadService {
    private static final String VERSION_COLUMNS = """
            id, certificate_id, version_number, status, created_at, transporter_version_id, source_version_id,
            previous_version_id, change_description
            """;

    private final JdbcClient db;
    private final RegistrationRequests requests;
    private final ObjectMapper json;

    public RegistrationReadService(JdbcClient db, RegistrationRequests requests, ObjectMapper json) {
        this.db = db;
        this.requests = requests;
        this.json = json;
    }

    public TransporterRequestDto request(UUID id) {
        var request = requests.find(id, false);
        var dto = request.dto();
        int type = dto.getType().getValue();
        var actor = CurrentAccount.id();
        if (!actor.equals(dto.getApplicantContactId()) && !actor.equals(dto.getExecutorId())
                && !mayReview(type) && !(type != 4 && mayRepresent(dto.getOrganizationId(), actor))) {
            throw notFound();
        }
        return withProposedData(request);
    }

    public DecisionDto decision(UUID requestId) {
        request(requestId);
        return requests.decision(requestId);
    }

    public List<TransporterRequestDto> listRequests(UUID transporter, Integer status, int offset, int limit) {
        var reviewerTypes = Arrays.stream(TransporterRequestType.values())
                .mapToInt(TransporterRequestType::getId).filter(this::mayReview).boxed().toList();
        return db.sql("""
                SELECT r.* FROM request r
                WHERE (r.applicant_contact_id = :actor OR r.executor_id = :actor OR r.type IN (:types)
                    OR (r.type <> 4 AND EXISTS (SELECT 1 FROM transporter_organization_users m
                        WHERE m.organization_id = r.organization_id AND m.user_id = :actor AND m.can_represent)))
                  AND (:transporter::uuid IS NULL OR r.transporter_id = :transporter::uuid)
                  AND (:status::integer IS NULL OR r.status = :status::integer)
                ORDER BY r.created_at DESC, r.id LIMIT :limit OFFSET :offset
                """).param("actor", CurrentAccount.id()).param("types", reviewerTypes.isEmpty() ? List.of(-1) : reviewerTypes)
                .param("transporter", transporter).param("status", status).param("limit", limit).param("offset", offset)
                .query((rs, row) -> withProposedData(RegistrationRequests.mapRequest(rs))).list();
    }

    public RegistrationCertificateDto certificate(UUID transporter) {
        requireCarrierAccess(transporter);
        return db.sql("""
                SELECT c.id, c.certificate_number, c.status, c.created_at,
                    (SELECT v.id FROM transporter_registration_certificate_version v
                     WHERE v.certificate_id = c.id ORDER BY v.version_number DESC LIMIT 1) AS current_version_id
                FROM transporter_registration_certificate c WHERE c.transporter_id = :transporter
                """).param("transporter", transporter)
                .query((rs, row) -> new RegistrationCertificateDto(rs.getObject("id", UUID.class), transporter,
                        rs.getString("certificate_number"), rs.getInt("status"), date(rs))
                        .currentVersionId(rs.getObject("current_version_id", UUID.class)))
                .optional().orElseThrow(RegistrationReadService::notFound);
    }

    public List<RegistrationCertificateVersionDto> certificateVersions(UUID certificate, int offset, int limit) {
        requireCertificateAccess(certificate);
        return db.sql("SELECT " + VERSION_COLUMNS + """
                FROM transporter_registration_certificate_version WHERE certificate_id = :certificate
                ORDER BY version_number DESC LIMIT :limit OFFSET :offset
                """).param("certificate", certificate).param("limit", limit).param("offset", offset)
                .query((rs, row) -> certificateVersion(rs)).list();
    }

    public RegistrationCertificateVersionDto certificateVersion(UUID certificate, UUID version) {
        requireCertificateAccess(certificate);
        return db.sql("SELECT " + VERSION_COLUMNS + """
                FROM transporter_registration_certificate_version WHERE certificate_id = :certificate AND id = :version
                """).param("certificate", certificate).param("version", version)
                .query((rs, row) -> certificateVersion(rs)).optional().orElseThrow(RegistrationReadService::notFound);
    }

    public TransporterVersionDto transporterVersion(UUID transporter, UUID version) {
        requireCarrierAccess(transporter);
        var dto = db.sql("""
                SELECT version_number, transport_kind_id, status, created_at FROM transporter_version
                WHERE transporter_id = :transporter AND id = :version
                """).param("transporter", transporter).param("version", version)
                .query((rs, row) -> new TransporterVersionDto(version, transporter, rs.getInt("version_number"),
                        rs.getObject("transport_kind_id", UUID.class), rs.getInt("status"), List.of(), date(rs)))
                .optional().orElseThrow(RegistrationReadService::notFound);
        dto.setLicenses(db.sql("""
                SELECT id, license_number, issued_at, expires_at, issuing_authority, status
                FROM transporter_license WHERE transporter_version_id = :version
                ORDER BY license_number, id
                """).param("version", version).query((rs, row) -> new TransporterLicenseDto(
                        rs.getObject("id", UUID.class), transporter, rs.getString("license_number"),
                        rs.getObject("issued_at", LocalDate.class), rs.getObject("expires_at", LocalDate.class),
                        rs.getString("issuing_authority"), rs.getInt("status"))).list());
        return dto;
    }

    private void requireCertificateAccess(UUID certificate) {
        var transporter = db.sql("SELECT transporter_id FROM transporter_registration_certificate WHERE id = :id")
                .param("id", certificate).query(UUID.class).optional().orElseThrow(RegistrationReadService::notFound);
        requireCarrierAccess(transporter);
    }

    private void requireCarrierAccess(UUID transporter) {
        UUID organization = db.sql("SELECT organization_id FROM transporter WHERE id = :id").param("id", transporter)
                .query(UUID.class).optional().orElseThrow(RegistrationReadService::notFound);
        UUID actor = CurrentAccount.id();
        boolean organizationMember = db.sql("""
                SELECT EXISTS (SELECT 1 FROM transporter_organization_users
                    WHERE organization_id = :organization AND user_id = :actor)
                """).param("organization", organization).param("actor", actor).query(Boolean.class).single();
        if (!organizationMember && Arrays.stream(TransporterRequestType.values())
                .mapToInt(TransporterRequestType::getId).noneMatch(this::mayReview)) {
            throw notFound();
        }
    }

    private boolean mayRepresent(UUID organization, UUID actor) {
        return db.sql("""
                SELECT EXISTS (SELECT 1 FROM transporter_organization_users
                    WHERE organization_id = :organization AND user_id = :actor AND can_represent)
                """).param("organization", organization).param("actor", actor).query(Boolean.class).single();
    }

    private boolean mayReview(int type) {
        return CurrentAccount.hasPermission(RegistrationRequests.permission(type, "decide"))
                || CurrentAccount.hasPermission(RegistrationRequests.permission(type, "approve"));
    }

    private TransporterRequestDto withProposedData(RegistrationRequests.Request request) {
        if (request.proposedData() != null) {
            request.dto().setProposedData(json.readValue(request.proposedData(), TransporterSnapshotInput.class));
        }
        return request.dto();
    }

    private static RegistrationCertificateVersionDto certificateVersion(ResultSet rs) throws SQLException {
        return new RegistrationCertificateVersionDto(rs.getObject("id", UUID.class),
                rs.getObject("certificate_id", UUID.class), rs.getInt("version_number"), rs.getInt("status"), date(rs))
                .transporterVersionId(rs.getObject("transporter_version_id", UUID.class))
                .sourceVersionId(rs.getObject("source_version_id", UUID.class))
                .previousVersionId(rs.getObject("previous_version_id", UUID.class))
                .changeDescription(rs.getString("change_description"));
    }

    private static String date(ResultSet rs) throws SQLException {
        return rs.getObject("created_at", LocalDateTime.class).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
    }
}
