package com.github.evseevda.domainlogicmodule.registration.service;

import static com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames.REGISTRATION_DECISION_APPROVED;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames.REGISTRATION_DECISION_RETURNED;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames.REGISTRATION_DECISION_SUBMITTED;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames.REGISTRATION_EXECUTOR_ASSIGNED;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.ADMIN_CANCELLATION;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.CARRIER_CANCELLATION;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.INITIAL_REGISTRATION;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.REGISTRATION_AMENDMENT;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.REGISTRATION_ASSIGNMENT;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.REGISTRATION_DECISION;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.REGISTRATION_REFUSAL;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.REGISTRATION_RETURN;
import static com.github.evseevda.domainlogicmodule.workflow.BpmnJobTypes.REGISTRATION_WITHDRAW;

import com.github.evseevda.domainlogic.model.CreateTransporterRequest;
import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.IssueDecision;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository.RegistrationRequestProjection;
import com.github.evseevda.domainlogicmodule.registration.repository.RegistrationRequestRepository;
import com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowCommands;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowJobHandler;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

/** Проверяет команды по регистрации перевозчика и выполняет переходы, полученные из Zeebe. */
@Service
@Profile("!mock-api")
public class RegistrationRequestService implements WorkflowJobHandler {

    private final RegistrationRequestRepository repository;
    private final WorkflowCommands workflow;
    private final RegistrationSnapshotService snapshots;

    /** Создаёт сервис переходов с журналом команд и сервисом снимков перевозчика. */
    public RegistrationRequestService(
            RegistrationRequestRepository repository,
            WorkflowCommands workflow,
            RegistrationSnapshotService snapshots) {
        this.repository = repository;
        this.workflow = workflow;
        this.snapshots = snapshots;
    }

    /**
     * Сохраняет черновик заявления, проверяя полномочия и данные перевозчика для выбранного
     * процесса.
     */
    @Transactional
    public TransporterRequestDto createRegistrationRequest(CreateTransporterRequest form) {
        UUID actor = CurrentAccount.getUserId();
        int type = form.getType().getValue();
        UUID transporter = form.getTransporterId();
        UUID organization = form.getOrganizationId();
        UUID source = null;
        String data = null;
        if (type == 1) {
            if (organization == null || transporter != null) {
                bad("Укажите организацию и данные нового перевозчика");
            }
            requireRepresentation(organization, actor);
            data = snapshots.validateAndSerializeTransporterSnapshot(form.getTransporterData());
        } else {
            if (transporter == null) {
                bad("Укажите перевозчика");
            }
            var current =
                    repository
                            .findTransporterById(transporter)
                            .orElseThrow(
                                    () -> failure(HttpStatus.NOT_FOUND, "Перевозчик не найден"));
            if (organization != null && !organization.equals(current.organization())) {
                bad("Организация не совпадает");
            }
            organization = current.organization();
            if (type == 4) {
                CurrentAccount.requirePermission(getPermissionName(type, "submit"));
            } else {
                requireRepresentation(organization, actor);
            }
            if (current.status() != 1) {
                conflict("Перевозчик не зарегистрирован");
            }
            source = snapshots.getCurrentTransporterVersionId(transporter);
            if (type == 2) {
                data = snapshots.validateAndSerializeTransporterSnapshot(form.getTransporterData());
            } else if (form.getTransporterData() != null) {
                bad("Для отмены используется действующая версия перевозчика");
            }
        }
        UUID id = UUID.randomUUID();
        repository.insertRequest(id, transporter, organization, source, data, actor, type);
        return getRegistrationRequestById(id, false).dto();
    }

    /** Переводит черновик в поданное заявление и ставит запуск BPMN в очередь. */
    @Transactional
    public TransporterRequestDto submitRegistrationRequest(UUID requestId) {
        var request = getRegistrationRequestById(requestId, true);
        requireApplicant(request);
        requireState(request, 0);
        int type = getRequestTypeId(request);
        if (type == 4) {
            CurrentAccount.requirePermission(getPermissionName(type, "submit"));
        } else {
            requireRepresentation(request.dto().getOrganizationId(), CurrentAccount.getUserId());
        }
        UUID transporter = request.dto().getTransporterId();
        if (transporter != null) {
            repository.lockTransporterById(transporter);
            if (!snapshots
                    .getCurrentTransporterVersionId(transporter)
                    .equals(request.dto().getSourceTransporterVersionId())) {
                conflict("Исходная версия перевозчика изменилась");
            }
            boolean occupied = repository.existsOpenRequestByTransporterId(transporter);
            if (occupied) {
                conflict("По перевозчику уже рассматривается заявление");
            }
        }
        repository.markSubmitted(requestId);
        workflow.enqueueProcessStart(requestId, getProcessId(type));
        return getRegistrationRequestById(requestId, false).dto();
    }

    /** Ставит в очередь назначение исполнителя или взятие заявления в работу. */
    @Transactional
    public TransporterRequestDto assignRequestExecutor(UUID requestId, UUID executorId) {
        var request = getRegistrationRequestById(requestId, true);
        requireState(request, 1);
        UUID actor = CurrentAccount.getUserId();
        int type = getRequestTypeId(request);
        CurrentAccount.requirePermission(getPermissionName(type, "decide"));
        if (!actor.equals(executorId)) {
            CurrentAccount.requirePermission(getPermissionName(type, "assign"));
            if (!hasPermission(executorId, getPermissionName(type, "decide"))) {
                bad("У назначаемого сотрудника нет разрешения вынести решение");
            }
        }
        enqueueWorkflowAction(
                request,
                REGISTRATION_ASSIGNMENT,
                REGISTRATION_EXECUTOR_ASSIGNED,
                Map.of("executorId", executorId.toString()));
        return request.dto();
    }

    /** Сохраняет новую редакцию решения исполнителя и ставит передачу на согласование в очередь. */
    @Transactional
    public DecisionDto issueRegistrationDecision(UUID requestId, IssueDecision form) {
        var request = getRegistrationRequestById(requestId, true);
        requireState(request, 2, 7);
        UUID actor = CurrentAccount.getUserId();
        CurrentAccount.requirePermission(getPermissionName(getRequestTypeId(request), "decide"));
        if (!actor.equals(request.dto().getExecutorId())) {
            forbidden();
        }
        repository.saveDraftDecision(
                UUID.randomUUID(),
                requestId,
                actor,
                form.getType().getValue(),
                form.getComment(),
                form.getChangeDescription());
        var decision = getDecisionByRequestId(requestId);
        enqueueWorkflowAction(
                request,
                REGISTRATION_DECISION,
                REGISTRATION_DECISION_SUBMITTED,
                Map.of("revision", decision.getRevision()));
        return decision;
    }

    /** Ставит в очередь согласование или возврат указанной редакции решения. */
    @Transactional
    public TransporterRequestDto reviewRegistrationDecision(
            UUID requestId, int revision, String returnReason) {
        var request = getRegistrationRequestById(requestId, true);
        requireState(request, 6);
        UUID reviewer = CurrentAccount.getUserId();
        CurrentAccount.requirePermission(getPermissionName(getRequestTypeId(request), "approve"));
        var decision = getDecisionByRequestId(requestId);
        if (decision.getRevision() != revision) {
            conflict("Редакция решения изменилась");
        }
        if (returnReason != null) {
            if (returnReason.isBlank()) {
                bad("Укажите причину возврата");
            }
            enqueueWorkflowAction(
                    request,
                    REGISTRATION_RETURN,
                    REGISTRATION_DECISION_RETURNED,
                    Map.of("revision", revision, "returnReason", returnReason.strip()));
        } else {
            int result = decision.getType().getValue();
            enqueueWorkflowAction(
                    request,
                    result == 2
                            ? REGISTRATION_REFUSAL
                            : getApprovalJobType(getRequestTypeId(request)),
                    REGISTRATION_DECISION_APPROVED,
                    Map.of("revision", revision, "reviewerId", reviewer.toString()));
        }
        return request.dto();
    }

    /** Ставит в очередь отзыв заявления, доступный только подателю до согласования. */
    @Transactional
    public TransporterRequestDto withdrawRegistrationRequest(UUID requestId) {
        var request = getRegistrationRequestById(requestId, true);
        requireApplicant(request);
        requireState(request, 1, 2, 6, 7);
        enqueueWorkflowAction(
                request,
                REGISTRATION_WITHDRAW,
                BpmnEventNames.REGISTRATION_WITHDRAWAL_REQUESTED,
                Map.of());
        return request.dto();
    }

    /** Выполняет переход заявления. Повтор задания возвращает сохранённый результат команды. */
    @Override
    @Transactional
    public Map<String, ?> handleJob(String job, JsonNode variables) {
        UUID id = uuid(variables, "requestId");
        UUID command = uuid(variables, "commandId");
        var request = getRegistrationRequestById(id, true);
        var processed = repository.findProcessedDecisionTypeByCommandIdAndRequestId(command, id);
        if (processed.isPresent()) {
            return processed.orElseThrow() == 0
                    ? Map.of()
                    : Map.of("decisionType", processed.orElseThrow());
        }
        if (!job.equals(request.pendingAction()) || !command.equals(request.pendingCommand())) {
            throw new IllegalStateException("Unexpected registration job " + job + " for " + id);
        }
        Integer result = null;
        switch (job) {
            case REGISTRATION_ASSIGNMENT -> {
                repository.assignExecutor(uuid(variables, "executorId"), id);
            }
            case REGISTRATION_DECISION -> {
                result = getDecisionByRequestId(id).getType().getValue();
                updateStatus(id, 6);
            }
            case REGISTRATION_RETURN -> {
                repository.saveReturnReason(variables.path("returnReason").asText(), id);
                updateStatus(id, 7);
            }
            case REGISTRATION_WITHDRAW -> updateStatus(id, 5);
            default -> finish(request, job, variables);
        }
        repository.insertProcessedCommand(command, id, result);
        repository.clearPendingAction(id);
        return result == null ? Map.of() : Map.of("decisionType", result);
    }

    private void finish(RegistrationRequestProjection request, String job, JsonNode vars) {
        UUID id = request.dto().getId();
        var decision = getDecisionByRequestId(id);
        if (decision.getRevision() != vars.path("revision").asInt()) {
            throw new IllegalStateException("DecisionEntity revision changed for " + id);
        }
        boolean approved = !REGISTRATION_REFUSAL.equals(job);
        if (approved != (decision.getType().getValue() == 1)) {
            throw new IllegalStateException("Unexpected decision result for " + id);
        }
        if (approved) {
            var dto = request.dto();
            String description = repository.findChangeDescriptionByRequestId(id).orElse(null);
            UUID transporter =
                    snapshots.applyRegistrationSnapshot(
                            dto.getOrganizationId(),
                            dto.getTransporterId(),
                            dto.getSourceTransporterVersionId(),
                            getRequestTypeId(request),
                            request.proposedData(),
                            description);
            repository.attachTransporter(transporter, id);
        }
        repository.markDecisionApproved(uuid(vars, "reviewerId"), id);
        updateStatus(id, approved ? 3 : 4);
    }

    private void enqueueWorkflowAction(
            RegistrationRequestProjection request,
            String action,
            String event,
            Map<String, ?> values) {
        UUID id = request.dto().getId();
        UUID command = UUID.randomUUID();
        repository.savePendingAction(id, action, command);
        workflow.enqueueMessage(command, id, event, values);
    }

    private void updateStatus(UUID id, int status) {
        repository.updateStatus(status, id);
    }

    DecisionDto getDecisionByRequestId(UUID id) {
        return repository
                .findDecisionByRequestId(id)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Решение не найдено"));
    }

    RegistrationRequestProjection getRegistrationRequestById(UUID requestId, boolean lock) {
        return repository
                .findRegistrationRequestById(requestId, lock)
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Заявление не найдено"));
    }

    private void requireApplicant(RegistrationRequestProjection request) {
        if (!request.dto().getApplicantContactId().equals(CurrentAccount.getUserId())) {
            forbidden();
        }
    }

    private static void requireState(RegistrationRequestProjection request, int... states) {
        if (request.pendingAction() != null
                || Arrays.stream(states).noneMatch(state -> state == request.dto().getStatus())) {
            conflict("Действие недоступно в текущем состоянии заявления");
        }
    }

    private void requireRepresentation(UUID org, UUID user) {
        boolean allowed = repository.mayRepresent(org, user);
        if (!allowed) {
            forbidden();
        }
    }

    private boolean hasPermission(UUID user, String permission) {
        return repository.hasPermission(user, permission);
    }

    static String getPermissionName(int type, String action) {
        return switch (type) {
                    case 1 -> "transporter.registration.";
                    case 2 -> "transporter.amendment.";
                    case 3 -> "transporter.carrier-cancellation.";
                    case 4 -> "transporter.admin-cancellation.";
                    default ->
                            throw new IllegalArgumentException("Unknown registration type " + type);
                }
                + action;
    }

    private static String getApprovalJobType(int type) {
        return switch (type) {
            case 1 -> INITIAL_REGISTRATION;
            case 2 -> REGISTRATION_AMENDMENT;
            case 3 -> CARRIER_CANCELLATION;
            case 4 -> ADMIN_CANCELLATION;
            default -> throw new IllegalArgumentException("Unknown registration type " + type);
        };
    }

    private static String getProcessId(int type) {
        return switch (type) {
            case 1 -> "blm_initial_registration";
            case 2 -> "blm_registration_amendment";
            case 3 -> "blm_carrier_cancellation";
            case 4 -> "blm_admin_cancellation";
            default -> throw new IllegalArgumentException("Unknown registration type " + type);
        };
    }

    private static int getRequestTypeId(RegistrationRequestProjection request) {
        return request.dto().getType().getValue();
    }

    private static UUID uuid(JsonNode vars, String name) {
        return UUID.fromString(vars.path(name).asText());
    }

    private static void forbidden() {
        throw failure(HttpStatus.FORBIDDEN, "Нет полномочий для этого действия");
    }

    private static void bad(String message) {
        throw failure(HttpStatus.BAD_REQUEST, message);
    }

    private static void conflict(String message) {
        throw failure(HttpStatus.CONFLICT, message);
    }

    static ResponseStatusException failure(HttpStatus status, String reason) {
        return new ResponseStatusException(status, reason);
    }
}
