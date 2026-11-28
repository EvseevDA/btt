package com.github.evseevda.domainlogicmodule.catalog.resource;

import com.github.evseevda.domainlogic.api.CatalogApi;
import com.github.evseevda.domainlogic.model.CreateRoute;
import com.github.evseevda.domainlogic.model.RouteDto;
import com.github.evseevda.domainlogic.model.TransportKindDto;
import com.github.evseevda.domainlogic.model.TransporterDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import com.github.evseevda.domainlogic.model.TransporterOrganizationDto;

import com.github.evseevda.domainlogicmodule.organization.service.OrganizationDirectory;
import org.springframework.beans.factory.ObjectProvider;
import com.github.evseevda.domainlogicmodule.catalog.service.CatalogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Предоставляет REST-доступ к транспортному каталогу и справочникам. */
@RestController
public class CatalogController implements CatalogApi {
    private final ObjectProvider<OrganizationDirectory> organizations;
    private final ObjectProvider<CatalogService> services;

    /** Создаёт контроллер и связывает его с прикладным сервисом. */
    public CatalogController(ObjectProvider<OrganizationDirectory> organizations, ObjectProvider<CatalogService> services) {
        this.organizations = organizations;
        this.services = services;
    }

    /** Возвращает маршрут по идентификатору. */
    @Override
    public ResponseEntity<RouteDto> getRoute(UUID id) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.route(id));
    }

    /** Возвращает вид транспорта по идентификатору. */
    @Override
    public ResponseEntity<TransportKindDto> getTransportKind(UUID id) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.kind(id));
    }

    /** Возвращает карточку перевозчика. */
    @Override
    public ResponseEntity<TransporterDto> getTransporter(UUID id) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.transporter(id));
    }

    /** Возвращает карточку зарегистрированной организации. */
    @Override
    public ResponseEntity<TransporterOrganizationDto> getTransporterOrganization(UUID id) {
        var service = organizations.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.get(id));
    }

    /** Возвращает страницу маршрутов с фильтром по виду транспорта. */
    @Override
    public ResponseEntity<List<RouteDto>> listRoutes(UUID transportKindId, Integer offset, Integer limit) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.routes(transportKindId, offset, limit));
    }

    /** Возвращает страницу справочника видов транспорта. */
    @Override
    public ResponseEntity<List<TransportKindDto>> listTransportKinds(Integer offset, Integer limit) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.kinds(offset, limit));
    }

    /** Возвращает лицензии последнего снимка перевозчика. */
    @Override
    public ResponseEntity<List<TransporterLicenseDto>> listTransporterLicenses(
            UUID transporterId,
            Integer offset,
            Integer limit
    ) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.licenses(transporterId, offset, limit));
    }

    /** Ищет организации по названию, ИНН или ОГРН. */
    @Override
    public ResponseEntity<List<TransporterOrganizationDto>> listTransporterOrganizations(
            String search, Integer offset, Integer limit) {
        var service = organizations.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.list(search, offset, limit));
    }

    /** Возвращает перевозчиков с фильтрами по организации и виду транспорта. */
    @Override
    public ResponseEntity<List<TransporterDto>> listTransporters(
            UUID organizationId,
            UUID transportKindId,
            Integer offset,
            Integer limit
    ) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.ok(service.transporters(organizationId, transportKindId, offset, limit));
    }

    /** Сохраняет запись, созданную уполномоченным пользователем. */
    @Override
    public ResponseEntity<RouteDto> createRoute(CreateRoute form) {
        var service = services.getIfAvailable();
        return service == null ? ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build()
                : ResponseEntity.status(HttpStatus.CREATED).body(service.createRoute(form));
    }
}
