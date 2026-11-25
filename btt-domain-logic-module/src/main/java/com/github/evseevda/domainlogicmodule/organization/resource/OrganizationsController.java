package com.github.evseevda.domainlogicmodule.organization.resource;

import com.github.evseevda.domainlogic.api.OrganizationsApi;
import com.github.evseevda.domainlogic.model.JoinOrganization;
import com.github.evseevda.domainlogic.model.OrganizationRequestDto;
import com.github.evseevda.domainlogic.model.OrganizationMemberPermissions;
import com.github.evseevda.domainlogic.model.RegisterOrganization;
import com.github.evseevda.domainlogic.model.ReviewOrganizationRequest;
import com.github.evseevda.domainlogicmodule.organization.service.OrganizationRequests;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class OrganizationsController implements OrganizationsApi {
    private final ObjectProvider<OrganizationRequests> requests;

    public OrganizationsController(ObjectProvider<OrganizationRequests> requests) {
        this.requests = requests;
    }

    @Override
    public ResponseEntity<OrganizationRequestDto> submitOrganizationRegistration(RegisterOrganization form) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.register(form));
    }

    @Override
    public ResponseEntity<OrganizationRequestDto> submitOrganizationMembership(JoinOrganization form) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.join(form));
    }

    @Override
    public ResponseEntity<Void> updateOrganizationMemberPermissions(UUID organizationId, UUID userId,
                                                                     OrganizationMemberPermissions permissions) {
        var service = requests.getIfAvailable();
        if (service == null) return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        service.updatePermissions(organizationId, userId, permissions.getCanRepresent(), permissions.getCanConfirmMembers());
        return ResponseEntity.noContent().build();
    }

    @Override
    public ResponseEntity<OrganizationRequestDto> getOrganizationRequest(UUID id) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<List<OrganizationRequestDto>> listOrganizationRequests(
            Integer type, Integer status, Integer offset, Integer limit) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.list(type, status, offset, limit));
    }

    @Override
    public ResponseEntity<OrganizationRequestDto> decideOrganizationRequest(UUID id, ReviewOrganizationRequest review) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.decide(id, review));
    }
}
