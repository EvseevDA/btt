package com.github.evseevda.domainlogicmodule.catalog.resource;

import com.github.evseevda.domainlogic.api.CatalogApi;
import com.github.evseevda.domainlogic.model.RouteDto;
import com.github.evseevda.domainlogic.model.TransportKindDto;
import com.github.evseevda.domainlogic.model.TransporterDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import com.github.evseevda.domainlogic.model.TransporterOrganizationDto;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class CatalogController implements CatalogApi {

    @Override
    public ResponseEntity<RouteDto> getRoute(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TransportKindDto> getTransportKind(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TransporterDto> getTransporter(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<TransporterOrganizationDto> getTransporterOrganization(UUID id) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<RouteDto>> listRoutes(UUID transportKindId, Integer offset, Integer limit) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<TransportKindDto>> listTransportKinds(Integer offset, Integer limit) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<TransporterLicenseDto>> listTransporterLicenses(
            UUID transporterId,
            Integer offset,
            Integer limit
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<TransporterOrganizationDto>> listTransporterOrganizations(Integer offset, Integer limit) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @Override
    public ResponseEntity<List<TransporterDto>> listTransporters(
            UUID organizationId,
            UUID transportKindId,
            Integer offset,
            Integer limit
    ) {
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }
}
