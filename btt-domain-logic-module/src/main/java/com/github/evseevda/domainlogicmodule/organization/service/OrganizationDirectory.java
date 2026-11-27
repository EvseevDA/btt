package com.github.evseevda.domainlogicmodule.organization.service;

import com.github.evseevda.domainlogic.model.TransporterOrganizationDto;
import com.github.evseevda.domainlogicmodule.persistence.OffsetPage;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganization;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.organization.repository.OrganizationRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Позволяет найти подключённое юридическое лицо для подачи заявки. */
@Service
@Profile("!mock-api")
public class OrganizationDirectory {
    private final OrganizationRepository repository;

    /** Создаёт сервис с доступом к базе данных. */
    public OrganizationDirectory(OrganizationRepository repository) {
        this.repository = repository;
    }

    /** Возвращает подключённую организацию по её идентификатору. */
    public TransporterOrganizationDto get(UUID id) {
        return repository.findById(id).map(OrganizationDirectory::dto)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Организация не найдена"));
    }

    /** Ищет подключённые организации по фрагменту названия или точному ИНН либо ОГРН. */
    public List<TransporterOrganizationDto> list(String search, int offset, int limit) {
        return repository.searchOrganizations(search == null ? "" : search.strip(), new OffsetPage(offset, limit)).stream()
                .map(OrganizationDirectory::dto).toList();
    }

    private static TransporterOrganizationDto dto(TransporterOrganization organization) {
        return new TransporterOrganizationDto(organization.getId(), organization.getName(), organization.getInn(),
                organization.getOgrn(), organization.getLegalAddress(),
                organization.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
