package com.github.evseevda.domainlogicmodule.organization.resource;

import com.github.evseevda.domainlogic.api.OrganizationsApi;
import com.github.evseevda.domainlogic.model.JoinOrganization;
import com.github.evseevda.domainlogic.model.OrganizationMemberPermissions;
import com.github.evseevda.domainlogic.model.OrganizationRequestDto;
import com.github.evseevda.domainlogic.model.RegisterOrganization;
import com.github.evseevda.domainlogic.model.ReviewOrganizationRequest;
import com.github.evseevda.domainlogicmodule.organization.service.OrganizationRequestService;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Предоставляет REST-операции по заявкам организаций и полномочиям их членов. */
@RestController
public class OrganizationController implements OrganizationsApi {
    private final ObjectProvider<OrganizationRequestService> requests;

    /** Создаёт контроллер и связывает его с прикладным сервисом. */
    public OrganizationController(ObjectProvider<OrganizationRequestService> requests) {
        this.requests = requests;
    }

    /** Подаёт заявку на подключение юридического лица к порталу. */
    @Override
    public ResponseEntity<OrganizationRequestDto> submitOrganizationRegistration(
            RegisterOrganization form) {
        var service = requests.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED)
                        .body(service.submitOrganizationRegistration(form));
    }

    /** Подаёт заявку на вступление в существующую организацию. */
    @Override
    public ResponseEntity<OrganizationRequestDto> submitOrganizationMembership(
            JoinOrganization form) {
        var service = requests.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED)
                        .body(service.submitOrganizationMembership(form));
    }

    /** Изменяет права представительства и подтверждения членов организации. */
    @Override
    public ResponseEntity<Void> updateOrganizationMemberPermissions(
            UUID organizationId, UUID userId, OrganizationMemberPermissions permissions) {
        var service = requests.getIfAvailable();
        if (service == null) {
            return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
        }
        service.updateOrganizationMemberPermissions(
                organizationId,
                userId,
                permissions.getCanRepresent(),
                permissions.getCanConfirmMembers());
        return ResponseEntity.noContent().build();
    }

    /** Возвращает карточку доступной заявки организации. */
    @Override
    public ResponseEntity<OrganizationRequestDto> getOrganizationRequest(UUID id) {
        var service = requests.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.getOrganizationRequestById(id));
    }

    /** Возвращает заявки, доступные пользователю или уполномоченному проверяющему. */
    @Override
    public ResponseEntity<List<OrganizationRequestDto>> listOrganizationRequests(
            Integer type, Integer status, Integer offset, Integer limit) {
        var service = requests.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(
                        service.findAllOrganizationRequests(type, status, offset, limit));
    }

    /** Принимает решение по заявке для последующей обработки процессом. */
    @Override
    public ResponseEntity<OrganizationRequestDto> decideOrganizationRequest(
            UUID id, ReviewOrganizationRequest review) {
        var service = requests.getIfAvailable();
        return service == null
                ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.accepted().body(service.decideOrganizationRequest(id, review));
    }
}
