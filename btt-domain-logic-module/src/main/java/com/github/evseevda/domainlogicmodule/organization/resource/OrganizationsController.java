package com.github.evseevda.domainlogicmodule.organization.resource;

import com.github.evseevda.domainlogic.api.OrganizationsApi;
import com.github.evseevda.domainlogic.model.JoinOrganization;
import com.github.evseevda.domainlogic.model.OrganizationRequestDto;
import com.github.evseevda.domainlogic.model.RegisterOrganization;
import com.github.evseevda.domainlogic.model.ReviewOrganizationRequest;
import com.github.evseevda.domainlogicmodule.organization.service.OrganizationRequests;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

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
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<OrganizationRequestDto> getOrganizationRequest(UUID id) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.get(id));
    }

    @Override
    public ResponseEntity<OrganizationRequestDto> decideOrganizationRequest(UUID id, ReviewOrganizationRequest review) {
        var service = requests.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.decide(id, review));
    }
}
