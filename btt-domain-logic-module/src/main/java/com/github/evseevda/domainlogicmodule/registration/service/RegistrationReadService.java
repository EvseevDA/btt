package com.github.evseevda.domainlogicmodule.registration.service;

import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateVersionDto;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogic.model.TransporterSnapshotInput;
import com.github.evseevda.domainlogic.model.TransporterVersionDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.Request;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationHistoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/** Читает заявления и снимки регистрации с учётом прав вызывающего пользователя. */
@Service
@Profile("!mock-api")
@Transactional(readOnly = true)
public class RegistrationReadService {
    private final RegistrationHistoryRepository repository;
    private final RegistrationRequests requests;
    private final ObjectMapper json;

    /** Создаёт сервис чтения заявлений и исторических снимков. */
    public RegistrationReadService(RegistrationHistoryRepository repository, RegistrationRequests requests, ObjectMapper json) {
        this.repository = repository;
        this.requests = requests;
        this.json = json;
    }

    /** Возвращает карточку заявления с предложенным снимком после проверки видимости. */
    public TransporterRequestDto request(UUID id) {
        var request = requests.find(id, false);
        var dto = request.dto();
        int type = dto.getType().getValue();
        var actor = CurrentAccount.id();
        if (!actor.equals(dto.getApplicantContactId()) && !actor.equals(dto.getExecutorId())
                && !mayReview(type) && !(type != 4 && mayRepresent(dto.getOrganizationId(), actor))) {
            throw notFound();
        }
        return withProposedData(request);
    }

    /** Возвращает решение по заявлению, которое пользователь вправе просматривать. */
    public DecisionDto decision(UUID requestId) {
        request(requestId);
        return requests.decision(requestId);
    }

    /** Читает страницу доступных заявлений с фильтрами по перевозчику и состоянию. */
    public List<TransporterRequestDto> listRequests(UUID transporter, Integer type, Integer status, int offset, int limit) {
        var reviewerTypes = Arrays.stream(TransporterRequestType.values())
                .mapToInt(TransporterRequestType::getId).filter(this::mayReview).boxed().toList();
        return repository.visibleRequests(CurrentAccount.id(), reviewerTypes, transporter, type, status, limit, offset).stream().map(this::withProposedData).toList();
    }

    /** Возвращает РУ перевозчика и идентификатор его последней версии. */
    public RegistrationCertificateDto certificate(UUID transporter) {
        requireCarrierAccess(transporter);
        return repository.findCertificate(transporter).orElseThrow(RegistrationReadService::notFound);
    }

    /** Возвращает версии РУ в порядке от новой к старой. */
    public List<RegistrationCertificateVersionDto> certificateVersions(UUID certificate, int offset, int limit) {
        requireCertificateAccess(certificate);
        return repository.certificateVersions(certificate, limit, offset);
    }

    /** Возвращает конкретную версию, принадлежащую указанному РУ. */
    public RegistrationCertificateVersionDto certificateVersion(UUID certificate, UUID version) {
        requireCertificateAccess(certificate);
        return repository.findCertificateVersion(certificate, version).orElseThrow(RegistrationReadService::notFound);
    }

    /** Возвращает исторический снимок перевозчика вместе с лицензиями этой версии. */
    public TransporterVersionDto transporterVersion(UUID transporter, UUID version) {
        requireCarrierAccess(transporter);
        var dto = repository.findTransporterVersion(transporter, version).orElseThrow(RegistrationReadService::notFound);
        dto.setLicenses(repository.licenses(version, transporter));
        return dto;
    }

    private void requireCertificateAccess(UUID certificate) {
        var transporter = repository.findCertificateTransporter(certificate).orElseThrow(RegistrationReadService::notFound);
        requireCarrierAccess(transporter);
    }

    private void requireCarrierAccess(UUID transporter) {
        UUID organization = repository.findOrganization(transporter).orElseThrow(RegistrationReadService::notFound);
        UUID actor = CurrentAccount.id();
        boolean organizationMember = repository.isOrganizationMember(organization, actor);
        if (!organizationMember && Arrays.stream(TransporterRequestType.values())
                .mapToInt(TransporterRequestType::getId).noneMatch(this::mayReview)) {
            throw notFound();
        }
    }

    private boolean mayRepresent(UUID organization, UUID actor) {
        return repository.mayRepresent(organization, actor);
    }

    private boolean mayReview(int type) {
        return CurrentAccount.hasPermission(RegistrationRequests.permission(type, "decide"))
                || CurrentAccount.hasPermission(RegistrationRequests.permission(type, "approve"));
    }

    private TransporterRequestDto withProposedData(Request request) {
        if (request.proposedData() != null) {
            request.dto().setProposedData(json.readValue(request.proposedData(), TransporterSnapshotInput.class));
        }
        return request.dto();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
    }
}
