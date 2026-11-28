package com.github.evseevda.domainlogicmodule.registration.service;

import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateDto;
import com.github.evseevda.domainlogic.model.RegistrationCertificateVersionDto;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogic.model.TransporterSnapshotInput;
import com.github.evseevda.domainlogic.model.TransporterVersionDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationHistoryRepository;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.RegistrationRequestProjection;
import com.github.evseevda.domainlogicmodule.registration.request.domain.TransporterRequestType;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.ObjectMapper;

/** Читает заявления и снимки регистрации с учётом прав вызывающего пользователя. */
@Service
@Profile("!mock-api")
@Transactional(readOnly = true)
public class RegistrationReadService {
    private final RegistrationHistoryRepository repository;
    private final RegistrationRequestService requests;
    private final ObjectMapper json;

    /** Создаёт сервис чтения заявлений и исторических снимков. */
    public RegistrationReadService(
            RegistrationHistoryRepository repository,
            RegistrationRequestService requests,
            ObjectMapper json) {
        this.repository = repository;
        this.requests = requests;
        this.json = json;
    }

    /** Возвращает карточку заявления с предложенным снимком после проверки видимости. */
    public TransporterRequestDto getRegistrationRequestById(UUID requestId) {
        var request = requests.getRegistrationRequestById(requestId, false);
        var dto = request.dto();
        int type = dto.getType().getValue();
        var actor = CurrentAccount.getUserId();
        if (!actor.equals(dto.getApplicantContactId())
                && !actor.equals(dto.getExecutorId())
                && !canReviewRequestType(type)
                && !(type != 4 && mayRepresent(dto.getOrganizationId(), actor))) {
            throw notFound();
        }
        return withProposedData(request);
    }

    /** Возвращает решение по заявлению, которое пользователь вправе просматривать. */
    public DecisionDto getDecisionByRequestId(UUID requestId) {
        getRegistrationRequestById(requestId);
        return requests.getDecisionByRequestId(requestId);
    }

    /** Читает страницу доступных заявлений с фильтрами по перевозчику и состоянию. */
    public List<TransporterRequestDto> findAllRegistrationRequests(
            UUID transporter, Integer type, Integer status, int offset, int limit) {
        var reviewerTypes =
                Arrays.stream(TransporterRequestType.values())
                        .mapToInt(TransporterRequestType::getId)
                        .filter(this::canReviewRequestType)
                        .boxed()
                        .toList();
        return repository
                .findAllVisibleRequests(
                        CurrentAccount.getUserId(),
                        reviewerTypes,
                        transporter,
                        type,
                        status,
                        limit,
                        offset)
                .stream()
                .map(this::withProposedData)
                .toList();
    }

    /** Возвращает РУ перевозчика и идентификатор его последней версии. */
    public RegistrationCertificateDto getRegistrationCertificateByTransporterId(
            UUID transporterId) {
        requireCarrierAccess(transporterId);
        return repository
                .findCertificateByTransporterId(transporterId)
                .orElseThrow(RegistrationReadService::notFound);
    }

    /** Возвращает версии РУ в порядке от новой к старой. */
    public List<RegistrationCertificateVersionDto> findAllCertificateVersionsByCertificateId(
            UUID certificateId, int offset, int limit) {
        requireCertificateAccess(certificateId);
        return repository.findAllCertificateVersionsByCertificateId(certificateId, limit, offset);
    }

    /** Возвращает конкретную версию, принадлежащую указанному РУ. */
    public RegistrationCertificateVersionDto getCertificateVersionById(
            UUID certificateId, UUID versionId) {
        requireCertificateAccess(certificateId);
        return repository
                .findCertificateVersionByCertificateIdAndId(certificateId, versionId)
                .orElseThrow(RegistrationReadService::notFound);
    }

    /** Возвращает исторический снимок перевозчика вместе с лицензиями этой версии. */
    public TransporterVersionDto getTransporterVersionById(UUID transporterId, UUID versionId) {
        requireCarrierAccess(transporterId);
        var dto =
                repository
                        .findTransporterVersionByTransporterIdAndId(transporterId, versionId)
                        .orElseThrow(RegistrationReadService::notFound);
        dto.setLicenses(repository.findAllLicensesByTransporterVersionId(versionId, transporterId));
        return dto;
    }

    private void requireCertificateAccess(UUID certificate) {
        var transporter =
                repository
                        .findTransporterIdByCertificateId(certificate)
                        .orElseThrow(RegistrationReadService::notFound);
        requireCarrierAccess(transporter);
    }

    private void requireCarrierAccess(UUID transporter) {
        UUID organization =
                repository
                        .findOrganizationIdByTransporterId(transporter)
                        .orElseThrow(RegistrationReadService::notFound);
        UUID actor = CurrentAccount.getUserId();
        boolean organizationMember = repository.isOrganizationMember(organization, actor);
        if (!organizationMember
                && Arrays.stream(TransporterRequestType.values())
                        .mapToInt(TransporterRequestType::getId)
                        .noneMatch(this::canReviewRequestType)) {
            throw notFound();
        }
    }

    private boolean mayRepresent(UUID organization, UUID actor) {
        return repository.mayRepresent(organization, actor);
    }

    private boolean canReviewRequestType(int type) {
        return CurrentAccount.hasPermission(
                        RegistrationRequestService.getPermissionName(type, "decide"))
                || CurrentAccount.hasPermission(
                        RegistrationRequestService.getPermissionName(type, "approve"));
    }

    private TransporterRequestDto withProposedData(RegistrationRequestProjection request) {
        if (request.proposedData() != null) {
            request.dto()
                    .setProposedData(
                            json.readValue(request.proposedData(), TransporterSnapshotInput.class));
        }
        return request.dto();
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Запись не найдена");
    }
}
