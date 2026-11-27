package com.github.evseevda.domainlogicmodule.organization.service;

import com.github.evseevda.domainlogic.model.JoinOrganization;
import com.github.evseevda.domainlogic.model.OrganizationRequestDto;
import com.github.evseevda.domainlogic.model.RegisterOrganization;
import com.github.evseevda.domainlogic.model.ReviewOrganizationRequest;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowCommands;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowJobHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import com.github.evseevda.domainlogicmodule.organization.repository.OrganizationRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.*;

/** Обрабатывает заявки на подключение организаций и вступление в них, а также итоговые задачи Zeebe. */
@Service
@Profile("!mock-api")
public class OrganizationRequests implements WorkflowJobHandler {
    private static final String REVIEW_REGISTRATION = "organization.registration.decide";


    private final OrganizationRequestRepository repository;
    private final WorkflowCommands workflow;

    /** Создаёт сервис заявок с доступом к базе и журналу команд. */
    public OrganizationRequests(OrganizationRequestRepository repository, WorkflowCommands workflow) {
        this.repository = repository;
        this.workflow = workflow;
    }

    /** Подаёт заявку на подключение юридического лица и ставит запуск проверки в очередь. */
    @Transactional
    public OrganizationRequestDto register(RegisterOrganization form) {
        UUID applicant = CurrentAccount.id();
        UUID request = UUID.randomUUID();
        if (form.getName().isBlank() || form.getLegalAddress().isBlank() || form.getAuthorityEvidence().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Заполните реквизиты и подтверждение полномочий");
        }
        boolean alreadyRegistered = repository.existsByInnOrOgrn(form.getInn(), form.getOgrn());
        if (alreadyRegistered) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Организация уже подключена");
        }
        try {
            repository.insertRegistration(request, applicant, form.getName().strip(), form.getInn(), form.getOgrn(), form.getLegalAddress().strip(), form.getAuthorityEvidence().strip());
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка организации уже рассматривается", ex);
        }
        workflow.start(request, "blm_organization_registration");
        return get(request);
    }

    /** Создаёт заявку на членство для текущего пользователя и запускает её проверку. */
    @Transactional
    public OrganizationRequestDto join(JoinOrganization form) {
        UUID applicant = CurrentAccount.id();
        UUID organization = form.getOrganizationId();
        boolean exists = repository.organizationExists(organization);
        if (!exists) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        boolean member = repository.isMember(organization, applicant);
        if (member) throw new ResponseStatusException(HttpStatus.CONFLICT, "Пользователь уже состоит в организации");
        UUID request = UUID.randomUUID();
        try {
            repository.insertMembership(request, applicant, organization);
        } catch (DuplicateKeyException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Заявка уже рассматривается", ex);
        }
        workflow.start(request, "blm_organization_membership");
        return get(request);
    }

    /** Обновляет делегированные полномочия члена организации после проверки прав вызывающего. */
    @Transactional
    public void updatePermissions(UUID organization, UUID user, boolean represent, boolean confirm) {
        UUID actor = CurrentAccount.id();
        if (actor.equals(user)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Собственные полномочия меняет другой представитель");
        }
        if (!mayConfirm(organization, actor)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        int updated = repository.updatePermissions(organization, user, represent, confirm);
        if (updated == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /** Читает заявку, доступную подателю или уполномоченному проверяющему. */
    public OrganizationRequestDto get(UUID id) {
        var request = find(id, false);
        if (!request.getApplicantId().equals(CurrentAccount.id())) {
            requireReviewer(request, CurrentAccount.id());
        }
        return request;
    }

    /** Читает страницу собственных заявок и заявок, доступных для проверки. */
    @Transactional(readOnly = true)
    public List<OrganizationRequestDto> list(Integer type, Integer status, int offset, int limit) {
        UUID actor = CurrentAccount.id();
        return repository.visibleRequests(actor, CurrentAccount.hasPermission(REVIEW_REGISTRATION), type, status, offset, limit);
    }

    /** Сохраняет вердикт уполномоченного проверяющего и ставит сообщение процесса в очередь. */
    @Transactional
    public OrganizationRequestDto decide(UUID id, ReviewOrganizationRequest review) {
        UUID reviewer = CurrentAccount.id();
        var request = find(id, true);
        requireReviewer(request, reviewer);
        if (request.getApplicantId().equals(reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Нельзя подтвердить собственную заявку");
        }
        if (request.getStatus() != 1 || request.getDecidedById() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Решение уже принято");
        }
        if (!review.getApproved() && (review.getComment() == null || review.getComment().isBlank())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Укажите причину отказа");
        }
        repository.saveDecision(id, reviewer, review.getComment(), review.getApproved());
        String event = request.getType() == 1 ? BpmnEventNames.ORGANIZATION_REGISTRATION_DECISION_SUBMITTED
                : BpmnEventNames.ORGANIZATION_MEMBERSHIP_DECISION_SUBMITTED;
        workflow.message(id, event, Map.of("approved", review.getApproved()));
        return find(id, false);
    }

    /** Применяет сохранённый результат проверки и сохраняет организацию или членство при одобрении. */
    @Override
    @Transactional
    public Map<String, ?> handle(String type, JsonNode variables) {
        UUID id = UUID.fromString(variables.path("requestId").asText());
        var request = find(id, true);
        if (request.getStatus() != 1) return Map.of();
        boolean registration = ORGANIZATION_REGISTRATION_APPROVE.equals(type) || ORGANIZATION_REGISTRATION_REJECT.equals(type);
        boolean approved = ORGANIZATION_REGISTRATION_APPROVE.equals(type) || ORGANIZATION_MEMBERSHIP_APPROVE.equals(type);
        var pending = repository.pendingApproval(id);
        if (request.getDecidedById() == null || registration != (request.getType() == 1)
                || pending.isEmpty() || pending.orElseThrow() != approved) {
            throw new IllegalStateException("No matching organization decision for " + id);
        }
        UUID organization = request.getOrganizationId();
        if (approved) {
            if (registration) organization = connect(request);
            else addMember(organization, request.getApplicantId(), false);
        }
        repository.completeRequest(approved ? 2 : 3, organization, id);
        return Map.of();
    }

    private UUID connect(OrganizationRequestDto request) {
        var form = request.getOrganizationData();
        UUID organization = UUID.randomUUID();
        repository.insertOrganization(organization, form.getName(), form.getInn(), form.getOgrn(), form.getLegalAddress());
        addMember(organization, request.getApplicantId(), true);
        return organization;
    }

    private void addMember(UUID organization, UUID user, boolean firstRepresentative) {
        repository.insertMember(organization, user, firstRepresentative);
    }

    private void requireReviewer(OrganizationRequestDto request, UUID reviewer) {
        if (request.getType() == 1) {
            CurrentAccount.require(REVIEW_REGISTRATION);
        } else if (!mayConfirm(request.getOrganizationId(), reviewer)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private boolean mayConfirm(UUID organization, UUID user) {
        return repository.mayConfirm(organization, user);
    }

    private OrganizationRequestDto find(UUID id, boolean lock) {
        return repository.findRequest(id, lock)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

}
