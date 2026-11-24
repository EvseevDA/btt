package com.github.evseevda.domainlogicmodule.registration.resource;

import com.github.evseevda.domainlogic.api.RegistrationApi;
import com.github.evseevda.domainlogic.model.AssignRequestExecutor;
import com.github.evseevda.domainlogic.model.CreateTransporterRequest;
import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.IssueDecision;
import com.github.evseevda.domainlogic.model.RegistrationCertificateDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateVersionDto;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class RegistrationController implements RegistrationApi {

    @Override
    public ResponseEntity<TransporterRequestDto> assignRegistrationRequestExecutor(
            UUID requestId,
            AssignRequestExecutor assignRequestExecutor
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TransporterRequestDto> createRegistrationRequest(
            CreateTransporterRequest createTransporterRequest
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<RegistrationCertificateVersionDto> getCertificateVersion(UUID certificateId, UUID versionId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<RegistrationCertificateDto> getRegistrationCertificate(UUID transporterId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<DecisionDto> getRegistrationDecision(UUID requestId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TransporterRequestDto> getRegistrationRequest(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<DecisionDto> issueRegistrationDecision(UUID requestId, IssueDecision issueDecision) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<RegistrationCertificateVersionDto>> listCertificateVersions(
            UUID certificateId,
            Integer offset,
            Integer limit
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<TransporterRequestDto>> listRegistrationRequests(
            UUID transporterId,
            Integer status,
            Integer offset,
            Integer limit
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TransporterRequestDto> submitRegistrationRequest(UUID requestId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TransporterRequestDto> withdrawRegistrationRequest(UUID requestId) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
