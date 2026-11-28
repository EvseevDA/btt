package com.github.evseevda.domainlogicmodule.catalog.service;

import com.github.evseevda.domainlogic.model.CreateRoute;
import com.github.evseevda.domainlogic.model.RouteDto;
import com.github.evseevda.domainlogic.model.TransportKindDto;
import com.github.evseevda.domainlogic.model.TransporterDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import com.github.evseevda.domainlogicmodule.catalog.repository.RouteRepository;
import com.github.evseevda.domainlogicmodule.catalog.repository.TransportKindRepository;
import com.github.evseevda.domainlogicmodule.catalog.repository.TransporterLicenseRepository;
import com.github.evseevda.domainlogicmodule.catalog.repository.TransporterRepository;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.persistence.OffsetPage;
import com.github.evseevda.domainlogicmodule.route.domain.RouteEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransportKindEntity;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterEntity;
import com.github.evseevda.domainlogicmodule.transporter.service.TransporterLicenseMapper;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Читает справочники транспорта, маршруты, перевозчиков и лицензии их последних версий. */
@Service
@Profile("!mock-api")
@Transactional(readOnly = true)
public class CatalogService {
    private final RouteRepository routes;
    private final TransportKindRepository kinds;
    private final TransporterRepository transporters;
    private final TransporterLicenseRepository licenses;

    /** Создаёт сервис с доступом к базе данных. */
    public CatalogService(
            RouteRepository routes,
            TransportKindRepository kinds,
            TransporterRepository transporters,
            TransporterLicenseRepository licenses) {
        this.routes = routes;
        this.kinds = kinds;
        this.transporters = transporters;
        this.licenses = licenses;
    }

    /** Добавляет маршрут в справочник портала. */
    @Transactional
    public RouteDto createRoute(CreateRoute form) {
        CurrentAccount.requirePermission("catalog.routes.create");
        String source = form.getSource().strip();
        String destination = form.getDest().strip();
        if (source.isEmpty() || destination.isEmpty() || source.equalsIgnoreCase(destination)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Укажите разные пункты отправления и назначения");
        }
        var kind = kinds.findById(form.getTransportKindId()).orElseThrow(CatalogService::notFound);
        return toDto(routes.save(new RouteEntity(source, destination, form.getDuration(), kind)));
    }

    /** Возвращает маршрут по идентификатору. */
    public RouteDto getRouteById(UUID routeId) {
        return routes.findById(routeId)
                .map(CatalogService::toDto)
                .orElseThrow(CatalogService::notFound);
    }

    /** Возвращает маршруты с фильтром по виду транспорта. */
    public List<RouteDto> findAllRoutes(UUID transportKindId, int offset, int limit) {
        return routes
                .findAllMatchingTransportKind(transportKindId, new OffsetPage(offset, limit))
                .stream()
                .map(CatalogService::toDto)
                .toList();
    }

    /** Возвращает описание вида транспорта по идентификатору. */
    public TransportKindDto getTransportKindById(UUID transportKindId) {
        return kinds.findById(transportKindId)
                .map(CatalogService::toDto)
                .orElseThrow(CatalogService::notFound);
    }

    /** Возвращает страницу справочника видов транспорта. */
    public List<TransportKindDto> findAllTransportKinds(int offset, int limit) {
        return kinds.findAllByOrderByNameAscIdAsc(new OffsetPage(offset, limit)).stream()
                .map(CatalogService::toDto)
                .toList();
    }

    /** Возвращает текущие сведения о перевозчике. */
    public TransporterDto getTransporterById(UUID transporterId) {
        return transporters
                .findById(transporterId)
                .map(CatalogService::toDto)
                .orElseThrow(CatalogService::notFound);
    }

    /** Возвращает перевозчиков с фильтрами по организации и виду транспорта. */
    public List<TransporterDto> findAllTransporters(
            UUID organizationId, UUID transportKindId, int offset, int limit) {
        return transporters
                .findAllMatchingOrganizationAndTransportKind(
                        organizationId, transportKindId, new OffsetPage(offset, limit))
                .stream()
                .map(CatalogService::toDto)
                .toList();
    }

    /** Возвращает лицензии из последнего снимка перевозчика. */
    public List<TransporterLicenseDto> findAllCurrentLicensesByTransporterId(
            UUID transporterId, int offset, int limit) {
        getTransporterById(transporterId);
        return licenses
                .findAllCurrentByTransporterId(transporterId, new OffsetPage(offset, limit))
                .stream()
                .map(TransporterLicenseMapper::toDto)
                .toList();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись каталога не найдена");
    }

    private static RouteDto toDto(RouteEntity route) {
        return new RouteDto(
                route.getId(),
                route.getSource(),
                route.getDest(),
                route.getDuration(),
                route.getTransportKind().getId());
    }

    private static TransportKindDto toDto(TransportKindEntity kind) {
        return new TransportKindDto(kind.getId(), kind.getName())
                .description(kind.getDescription());
    }

    private static TransporterDto toDto(TransporterEntity transporter) {
        return new TransporterDto(
                transporter.getId(),
                transporter.getOrganization().getId(),
                transporter.getTransportKind().getId(),
                transporter.getStatus().getId(),
                transporter.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
