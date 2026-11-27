package com.github.evseevda.domainlogicmodule.catalog.service;

import com.github.evseevda.domainlogic.model.RouteDto;
import com.github.evseevda.domainlogic.model.TransportKindDto;
import com.github.evseevda.domainlogic.model.TransporterDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.catalog.repository.RouteRepository;
import com.github.evseevda.domainlogicmodule.catalog.repository.TransportKindRepository;
import com.github.evseevda.domainlogicmodule.catalog.repository.TransporterRepository;
import com.github.evseevda.domainlogicmodule.catalog.repository.TransporterLicenseRepository;
import com.github.evseevda.domainlogicmodule.persistence.OffsetPage;
import com.github.evseevda.domainlogicmodule.route.domain.Route;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransportKind;
import com.github.evseevda.domainlogicmodule.transporter.domain.Transporter;
import com.github.evseevda.domainlogicmodule.transporter.service.TransporterLicenseMapper;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

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
    public CatalogService(RouteRepository routes, TransportKindRepository kinds, TransporterRepository transporters,
                          TransporterLicenseRepository licenses) {
        this.routes = routes;
        this.kinds = kinds;
        this.transporters = transporters;
        this.licenses = licenses;
    }

    /** Возвращает маршрут по идентификатору. */
    public RouteDto route(UUID id) {
        return routes.findById(id).map(CatalogService::dto).orElseThrow(CatalogService::notFound);
    }

    /** Возвращает маршруты с фильтром по виду транспорта. */
    public List<RouteDto> routes(UUID kind, int offset, int limit) {
        return routes.findRoutes(kind, new OffsetPage(offset, limit)).stream().map(CatalogService::dto).toList();
    }

    /** Возвращает описание вида транспорта по идентификатору. */
    public TransportKindDto kind(UUID id) {
        return kinds.findById(id).map(CatalogService::dto).orElseThrow(CatalogService::notFound);
    }

    /** Возвращает страницу справочника видов транспорта. */
    public List<TransportKindDto> kinds(int offset, int limit) {
        return kinds.findAllByOrderByNameAscIdAsc(new OffsetPage(offset, limit)).stream().map(CatalogService::dto).toList();
    }

    /** Возвращает текущие сведения о перевозчике. */
    public TransporterDto transporter(UUID id) {
        return transporters.findById(id).map(CatalogService::dto).orElseThrow(CatalogService::notFound);
    }

    /** Возвращает перевозчиков с фильтрами по организации и виду транспорта. */
    public List<TransporterDto> transporters(UUID organization, UUID kind, int offset, int limit) {
        return transporters.findTransporters(organization, kind, new OffsetPage(offset, limit)).stream()
                .map(CatalogService::dto).toList();
    }

    /** Возвращает лицензии из последнего снимка перевозчика. */
    public List<TransporterLicenseDto> licenses(UUID transporter, int offset, int limit) {
        transporter(transporter);
        return licenses.findCurrentLicenses(transporter, new OffsetPage(offset, limit)).stream()
                .map(TransporterLicenseMapper::toDto).toList();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись каталога не найдена");
    }

    private static RouteDto dto(Route route) {
        return new RouteDto(route.getId(), route.getSource(), route.getDest(), route.getDuration(), route.getTransportKind().getId());
    }

    private static TransportKindDto dto(TransportKind kind) {
        return new TransportKindDto(kind.getId(), kind.getName()).description(kind.getDescription());
    }

    private static TransporterDto dto(Transporter transporter) {
        return new TransporterDto(transporter.getId(), transporter.getOrganization().getId(), transporter.getTransportKind().getId(),
                transporter.getStatus().getId(), transporter.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
