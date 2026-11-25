package com.github.evseevda.domainlogicmodule.organization.service;

import com.github.evseevda.domainlogic.model.TransporterOrganizationDto;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
@Profile("!mock-api")
public class OrganizationDirectory {
    private final JdbcClient db;

    public OrganizationDirectory(JdbcClient db) {
        this.db = db;
    }

    public TransporterOrganizationDto get(UUID id) {
        return db.sql("""
                SELECT id, name, inn, ogrn, legal_address, created_at FROM transporter_organization WHERE id = :id
                """).param("id", id).query((rs, row) -> organization(rs)).optional()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Организация не найдена"));
    }

    public List<TransporterOrganizationDto> list(String search, int offset, int limit) {
        return db.sql("""
                SELECT id, name, inn, ogrn, legal_address, created_at FROM transporter_organization
                WHERE (:search = '' OR strpos(lower(name), lower(:search)) > 0 OR inn = :search OR ogrn = :search)
                ORDER BY name, id LIMIT :limit OFFSET :offset
                """).param("search", search == null ? "" : search.strip()).param("offset", offset).param("limit", limit)
                .query((rs, row) -> organization(rs)).list();
    }

    private static TransporterOrganizationDto organization(ResultSet rs) throws SQLException {
        return new TransporterOrganizationDto(rs.getObject("id", UUID.class), rs.getString("name"),
                rs.getString("inn"), rs.getString("ogrn"), rs.getString("legal_address"),
                rs.getObject("created_at", LocalDateTime.class).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
