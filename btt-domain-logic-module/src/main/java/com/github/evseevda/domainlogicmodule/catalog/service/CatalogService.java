package com.github.evseevda.domainlogicmodule.catalog.service;

import com.github.evseevda.domainlogic.model.RouteDto;
import com.github.evseevda.domainlogic.model.TransportKindDto;
import com.github.evseevda.domainlogic.model.TransporterDto;
import com.github.evseevda.domainlogic.model.TransporterLicenseDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Читает справочники транспорта, маршруты, перевозчиков и лицензии их последних версий. */
@Service
@Profile("!mock-api")
public class CatalogService {
    private final JdbcClient db;

    /** Создаёт сервис с доступом к базе данных. */
    public CatalogService(JdbcClient db) {
        this.db = db;
    }

    /** Возвращает маршрут по идентификатору. */
    public RouteDto route(UUID id) {
        return db.sql("SELECT * FROM route WHERE id = :id").param("id", id)
                .query((rs, row) -> route(rs)).optional().orElseThrow(CatalogService::notFound);
    }

    /** Возвращает маршруты с фильтром по виду транспорта. */
    public List<RouteDto> routes(UUID kind, int offset, int limit) {
        return db.sql("""
                SELECT * FROM route WHERE (:kind::uuid IS NULL OR transport_kind_id = :kind::uuid)
                ORDER BY source, dest, id LIMIT :limit OFFSET :offset
                """).param("kind", kind).param("offset", offset).param("limit", limit)
                .query((rs, row) -> route(rs)).list();
    }

    /** Возвращает описание вида транспорта по идентификатору. */
    public TransportKindDto kind(UUID id) {
        return db.sql("SELECT * FROM transport_kind WHERE id = :id").param("id", id)
                .query((rs, row) -> kind(rs)).optional().orElseThrow(CatalogService::notFound);
    }

    /** Возвращает страницу справочника видов транспорта. */
    public List<TransportKindDto> kinds(int offset, int limit) {
        return db.sql("SELECT * FROM transport_kind ORDER BY name, id LIMIT :limit OFFSET :offset")
                .param("offset", offset).param("limit", limit).query((rs, row) -> kind(rs)).list();
    }

    /** Возвращает текущие сведения о перевозчике. */
    public TransporterDto transporter(UUID id) {
        return db.sql("SELECT * FROM transporter WHERE id = :id").param("id", id)
                .query((rs, row) -> transporter(rs)).optional().orElseThrow(CatalogService::notFound);
    }

    /** Возвращает перевозчиков с фильтрами по организации и виду транспорта. */
    public List<TransporterDto> transporters(UUID organization, UUID kind, int offset, int limit) {
        return db.sql("""
                SELECT * FROM transporter
                WHERE (:org::uuid IS NULL OR organization_id = :org::uuid)
                  AND (:kind::uuid IS NULL OR transport_kind_id = :kind::uuid)
                ORDER BY created_at DESC, id LIMIT :limit OFFSET :offset
                """).param("org", organization).param("kind", kind).param("offset", offset).param("limit", limit)
                .query((rs, row) -> transporter(rs)).list();
    }

    /** Возвращает лицензии из последнего снимка перевозчика. */
    public List<TransporterLicenseDto> licenses(UUID transporter, int offset, int limit) {
        transporter(transporter);
        return db.sql("""
                SELECT id, license_number, issued_at, expires_at, issuing_authority, status FROM transporter_license
                WHERE transporter_version_id = (SELECT id FROM transporter_version WHERE transporter_id = :id
                                                 ORDER BY version_number DESC LIMIT 1)
                ORDER BY license_number, id LIMIT :limit OFFSET :offset
                """).param("id", transporter).param("offset", offset).param("limit", limit)
                .query((rs, row) -> new TransporterLicenseDto(rs.getObject("id", UUID.class), transporter,
                        rs.getString("license_number"), rs.getObject("issued_at", LocalDate.class),
                        rs.getObject("expires_at", LocalDate.class), rs.getString("issuing_authority"),
                        rs.getInt("status"))).list();
    }

    private static RouteDto route(ResultSet rs) throws SQLException {
        return new RouteDto(rs.getObject("id", UUID.class), rs.getString("source"), rs.getString("dest"),
                rs.getInt("duration"), rs.getObject("transport_kind_id", UUID.class));
    }

    private static TransportKindDto kind(ResultSet rs) throws SQLException {
        return new TransportKindDto(rs.getObject("id", UUID.class), rs.getString("name"))
                .description(rs.getString("description"));
    }

    private static TransporterDto transporter(ResultSet rs) throws SQLException {
        return new TransporterDto(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                rs.getObject("transport_kind_id", UUID.class), rs.getInt("status"),
                rs.getObject("created_at", LocalDateTime.class).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись каталога не найдена");
    }
}
