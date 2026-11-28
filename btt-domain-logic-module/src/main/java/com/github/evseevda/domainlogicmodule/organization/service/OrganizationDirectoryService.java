package com.github.evseevda.domainlogicmodule.organization.service;

import com.github.evseevda.domainlogic.model.TransporterOrganizationDto;
import com.github.evseevda.domainlogicmodule.organization.repository.OrganizationRepository;
import com.github.evseevda.domainlogicmodule.persistence.OffsetPage;
import com.github.evseevda.domainlogicmodule.transporter.domain.TransporterOrganizationEntity;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Позволяет найти подключённое юридическое лицо для подачи заявки. */
@Service
@Profile("!mock-api")
public class OrganizationDirectoryService {
    private final OrganizationRepository repository;

    /** Создаёт сервис с доступом к базе данных. */
    public OrganizationDirectoryService(OrganizationRepository repository) {
        this.repository = repository;
    }

    /** Возвращает подключённую организацию по её идентификатору. */
    public TransporterOrganizationDto getOrganizationById(UUID organizationId) {
        return repository
                .findById(organizationId)
                .map(OrganizationDirectoryService::toDto)
                .orElseThrow(
                        () ->
                                new ResponseStatusException(
                                        HttpStatus.NOT_FOUND, "Организация не найдена"));
    }

    /** Ищет подключённые организации по фрагменту названия или точному ИНН либо ОГРН. */
    public List<TransporterOrganizationDto> findAllOrganizations(
            String search, int offset, int limit) {
        return repository
                .findAllMatchingSearch(
                        search == null ? "" : search.strip(), new OffsetPage(offset, limit))
                .stream()
                .map(OrganizationDirectoryService::toDto)
                .toList();
    }

    private static TransporterOrganizationDto toDto(TransporterOrganizationEntity organization) {
        return new TransporterOrganizationDto(
                organization.getId(),
                organization.getName(),
                organization.getInn(),
                organization.getOgrn(),
                organization.getLegalAddress(),
                organization.getCreatedAt().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}
