package com.github.evseevda.domainlogicmodule.registration.service;

import com.github.evseevda.domainlogic.model.CreateTransporterRequest;
import com.github.evseevda.domainlogic.model.DecisionDto;
import com.github.evseevda.domainlogic.model.IssueDecision;
import com.github.evseevda.domainlogic.model.TransporterRequestDto;
import com.github.evseevda.domainlogicmodule.identity.security.CurrentAccount;
import com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowCommands;
import com.github.evseevda.domainlogicmodule.workflow.WorkflowJobHandler;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames.*;

/** Проверяет команды по регистрации перевозчика и выполняет переходы, полученные из Zeebe. */
@Service
@Profile("!mock-api")
public class RegistrationRequests implements WorkflowJobHandler {
    private static final String ASSIGN_JOB = "blm_save_registration_assignment";
    private static final String DECISION_JOB = "blm_save_registration_decision";
    private static final String RETURN_JOB = "blm_return_registration_decision";
    private static final String WITHDRAW_JOB = "blm_withdraw_registration_request";
    private static final String REFUSAL_JOB = "blm_finalize_registration_refusal";

    private final JdbcClient db;
    private final WorkflowCommands workflow;
    private final RegistrationSnapshots snapshots;

    /** Создаёт сервис переходов с журналом команд и сервисом снимков перевозчика. */
    public RegistrationRequests(JdbcClient db, WorkflowCommands workflow, RegistrationSnapshots snapshots) {
        this.db = db;
        this.workflow = workflow;
        this.snapshots = snapshots;
    }

    /** Сохраняет черновик заявления, проверяя полномочия и данные перевозчика для выбранного процесса. */
    @Transactional
    public TransporterRequestDto create(CreateTransporterRequest form) {
        UUID actor = CurrentAccount.id();
        int type = form.getType().getValue();
        UUID transporter = form.getTransporterId();
        UUID organization = form.getOrganizationId();
        UUID source = null;
        String data = null;
        if (type == 1) {
            if (organization == null || transporter != null) bad("Укажите организацию и данные нового перевозчика");
            requireRepresentation(organization, actor);
            data = snapshots.validateAndSerialize(form.getTransporterData());
        } else {
            if (transporter == null) bad("Укажите перевозчика");
            var current = db.sql("SELECT organization_id, status FROM transporter WHERE id = :id")
                    .param("id", transporter)
                    .query((rs, row) -> new Carrier(rs.getObject("organization_id", UUID.class), rs.getInt("status")))
                    .optional().orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Перевозчик не найден"));
            if (organization != null && !organization.equals(current.organization())) bad("Организация не совпадает");
            organization = current.organization();
            if (type == 4) CurrentAccount.require(permission(type, "submit"));
            else requireRepresentation(organization, actor);
            if (current.status() != 1) conflict("Перевозчик не зарегистрирован");
            source = snapshots.currentVersion(transporter);
            if (type == 2) data = snapshots.validateAndSerialize(form.getTransporterData());
            else if (form.getTransporterData() != null) bad("Для отмены используется действующая версия перевозчика");
        }
        UUID id = UUID.randomUUID();
        db.sql("""
                INSERT INTO request (id, transporter_id, organization_id, source_transporter_version_id, proposed_data,
                    applicant_contact_id, type, status, created_at)
                VALUES (:id, :transporter, :organization, :source, :data, :applicant, :type, 0,
                    timezone('UTC', clock_timestamp()))
                """).param("id", id).param("transporter", transporter).param("organization", organization)
                .param("source", source).param("data", data).param("applicant", actor).param("type", type).update();
        return find(id, false).dto();
    }

    /** Переводит черновик в поданное заявление и ставит запуск BPMN в очередь. */
    @Transactional
    public TransporterRequestDto submit(UUID id) {
        var request = find(id, true);
        requireApplicant(request);
        requireState(request, 0);
        int type = type(request);
        if (type == 4) CurrentAccount.require(permission(type, "submit"));
        else requireRepresentation(request.dto().getOrganizationId(), CurrentAccount.id());
        UUID transporter = request.dto().getTransporterId();
        if (transporter != null) {
            db.sql("SELECT id FROM transporter WHERE id = :id FOR UPDATE").param("id", transporter).query(UUID.class).single();
            if (!snapshots.currentVersion(transporter).equals(request.dto().getSourceTransporterVersionId())) {
                conflict("Исходная версия перевозчика изменилась");
            }
            boolean occupied = db.sql("""
                    SELECT EXISTS (SELECT 1 FROM request WHERE transporter_id = :carrier AND status IN (1,2,6,7))
                    """).param("carrier", transporter).query(Boolean.class).single();
            if (occupied) conflict("По перевозчику уже рассматривается заявление");
        }
        db.sql("UPDATE request SET status = 1, submitted_at = timezone('UTC', clock_timestamp()) WHERE id = :id")
                .param("id", id).update();
        workflow.start(id, process(type));
        return find(id, false).dto();
    }

    /** Ставит в очередь назначение исполнителя или взятие заявления в работу. */
    @Transactional
    public TransporterRequestDto assign(UUID id, UUID executor) {
        var request = find(id, true);
        requireState(request, 1);
        UUID actor = CurrentAccount.id();
        int type = type(request);
        CurrentAccount.require(permission(type, "decide"));
        if (!actor.equals(executor)) {
            CurrentAccount.require(permission(type, "assign"));
            if (!hasPermission(executor, permission(type, "decide"))) {
                bad("У назначаемого сотрудника нет разрешения вынести решение");
            }
        }
        queue(request, ASSIGN_JOB, REGISTRATION_EXECUTOR_ASSIGNED, Map.of("executorId", executor.toString()));
        return request.dto();
    }

    /** Сохраняет новую редакцию решения исполнителя и ставит передачу на согласование в очередь. */
    @Transactional
    public DecisionDto issue(UUID id, IssueDecision form) {
        var request = find(id, true);
        requireState(request, 2, 7);
        UUID actor = CurrentAccount.id();
        CurrentAccount.require(permission(type(request), "decide"));
        if (!actor.equals(request.dto().getExecutorId())) forbidden();
        db.sql("""
                INSERT INTO decision (id, request_id, decided_by_id, type, comment, status, change_description, revision)
                VALUES (:decision, :request, :actor, :type, :comment, 0, :description, 1)
                ON CONFLICT (request_id) DO UPDATE SET decided_by_id = EXCLUDED.decided_by_id, type = EXCLUDED.type,
                    comment = EXCLUDED.comment, status = 0, change_description = EXCLUDED.change_description,
                    revision = decision.revision + 1
                """).param("decision", UUID.randomUUID()).param("request", id).param("actor", actor)
                .param("type", form.getType().getValue()).param("comment", form.getComment())
                .param("description", form.getChangeDescription()).update();
        var decision = decision(id);
        queue(request, DECISION_JOB, REGISTRATION_DECISION_SUBMITTED, Map.of("revision", decision.getRevision()));
        return decision;
    }

    /** Ставит в очередь согласование или возврат указанной редакции решения. */
    @Transactional
    public TransporterRequestDto review(UUID id, int revision, String returnReason) {
        var request = find(id, true);
        requireState(request, 6);
        UUID reviewer = CurrentAccount.id();
        CurrentAccount.require(permission(type(request), "approve"));
        var decision = decision(id);
        if (decision.getRevision() != revision) conflict("Редакция решения изменилась");
        if (returnReason != null) {
            if (returnReason.isBlank()) bad("Укажите причину возврата");
            queue(request, RETURN_JOB, REGISTRATION_DECISION_RETURNED,
                    Map.of("revision", revision, "returnReason", returnReason.strip()));
        } else {
            int result = decision.getType().getValue();
            queue(request, result == 2 ? REFUSAL_JOB : "blm_apply_" + process(type(request)).substring(4),
                    REGISTRATION_DECISION_APPROVED,
                    Map.of("revision", revision, "reviewerId", reviewer.toString()));
        }
        return request.dto();
    }

    /** Ставит в очередь отзыв заявления, доступный только подателю до согласования. */
    @Transactional
    public TransporterRequestDto withdraw(UUID id) {
        var request = find(id, true);
        requireApplicant(request);
        requireState(request, 1, 2, 6, 7);
        queue(request, WITHDRAW_JOB, BpmnEventNames.REGISTRATION_WITHDRAWAL_REQUESTED, Map.of());
        return request.dto();
    }

    /** Возвращает типы заданий, поддерживаемые этим обработчиком. */
    @Override
    public Set<String> jobTypes() {
        return Set.of(ASSIGN_JOB, DECISION_JOB, RETURN_JOB, WITHDRAW_JOB, REFUSAL_JOB, "blm_apply_initial_registration",
                "blm_apply_registration_amendment", "blm_apply_carrier_cancellation", "blm_apply_admin_cancellation");
    }

    /** Выполняет переход заявления. Повтор задания возвращает сохранённый результат команды. */
    @Override
    @Transactional
    public Map<String, ?> handle(String job, JsonNode variables) {
        UUID id = uuid(variables, "requestId");
        UUID command = uuid(variables, "commandId");
        var request = find(id, true);
        var processed = db.sql("SELECT coalesce(decision_type, 0) FROM registration_processed_command WHERE id = :id AND request_id = :request")
                .param("id", command).param("request", id).query(Integer.class).optional();
        if (processed.isPresent()) {
            return processed.orElseThrow() == 0 ? Map.of() : Map.of("decisionType", processed.orElseThrow());
        }
        if (!job.equals(request.pendingAction()) || !command.equals(request.pendingCommand())) {
            throw new IllegalStateException("Unexpected registration job " + job + " for " + id);
        }
        Integer result = null;
        switch (job) {
            case ASSIGN_JOB -> {
                db.sql("UPDATE request SET executor_id = :executor, status = 2 WHERE id = :id")
                        .param("executor", uuid(variables, "executorId")).param("id", id).update();
            }
            case DECISION_JOB -> {
                result = decision(id).getType().getValue();
                updateStatus(id, 6);
            }
            case RETURN_JOB -> {
                db.sql("UPDATE decision SET return_reason = :reason WHERE request_id = :id")
                        .param("reason", variables.path("returnReason").asText()).param("id", id).update();
                updateStatus(id, 7);
            }
            case WITHDRAW_JOB -> updateStatus(id, 5);
            default -> finish(request, job, variables);
        }
        db.sql("INSERT INTO registration_processed_command (id, request_id, decision_type) VALUES (:id, :request, :decision)")
                .param("id", command).param("request", id).param("decision", result).update();
        db.sql("UPDATE request SET pending_action = NULL, pending_command_id = NULL WHERE id = :id")
                .param("id", id).update();
        return result == null ? Map.of() : Map.of("decisionType", result);
    }

    private void finish(Request request, String job, JsonNode vars) {
        UUID id = request.dto().getId();
        var decision = decision(id);
        if (decision.getRevision() != vars.path("revision").asInt()) {
            throw new IllegalStateException("Decision revision changed for " + id);
        }
        boolean approved = !REFUSAL_JOB.equals(job);
        if (approved != (decision.getType().getValue() == 1)) {
            throw new IllegalStateException("Unexpected decision result for " + id);
        }
        if (approved) {
            var dto = request.dto();
            String description = db.sql("SELECT change_description FROM decision WHERE request_id = :id")
                    .param("id", id).query(String.class).optional().orElse(null);
            UUID transporter = snapshots.apply(dto.getOrganizationId(), dto.getTransporterId(), dto.getSourceTransporterVersionId(),
                    type(request), request.proposedData(), description);
            db.sql("UPDATE request SET transporter_id = :transporter WHERE id = :id")
                    .param("transporter", transporter).param("id", id).update();
        }
        db.sql("""
                UPDATE decision SET status = 1, approved_by_id = :reviewer, approved_at = timezone('UTC', clock_timestamp()),
                    decided_at = timezone('UTC', clock_timestamp()) WHERE request_id = :id
                """).param("reviewer", uuid(vars, "reviewerId")).param("id", id).update();
        updateStatus(id, approved ? 3 : 4);
    }

    private void queue(Request request, String action, String event, Map<String, ?> values) {
        UUID id = request.dto().getId();
        UUID command = UUID.randomUUID();
        db.sql("UPDATE request SET pending_action = :action, pending_command_id = :command WHERE id = :id")
                .param("id", id).param("action", action).param("command", command).update();
        workflow.message(command, id, event, values);
    }

    private void updateStatus(UUID id, int status) {
        db.sql("UPDATE request SET status = :status WHERE id = :id").param("status", status).param("id", id).update();
    }

    DecisionDto decision(UUID id) {
        return db.sql("SELECT * FROM decision WHERE request_id = :id").param("id", id)
                .query((rs, row) -> {
                    var dto = new DecisionDto(rs.getObject("id", UUID.class), id, rs.getObject("decided_by_id", UUID.class),
                            DecisionDto.TypeEnum.fromValue(rs.getInt("type")), rs.getInt("status"));
                    dto.setRevision(rs.getInt("revision"));
                    dto.setApprovedById(rs.getObject("approved_by_id", UUID.class));
                    dto.setReturnReason(rs.getString("return_reason"));
                    String comment = rs.getString("comment");
                    if (comment != null) dto.comment(comment);
                    var date = rs.getObject("decided_at", LocalDateTime.class);
                    if (date != null) dto.decidedAt(date.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                    return dto;
                }).optional().orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Решение не найдено"));
    }

    Request find(UUID id, boolean lock) {
        return db.sql("SELECT * FROM request WHERE id = :id" + (lock ? " FOR UPDATE" : "")).param("id", id)
                .query((rs, row) -> mapRequest(rs)).optional()
                .orElseThrow(() -> failure(HttpStatus.NOT_FOUND, "Заявление не найдено"));
    }

    static Request mapRequest(java.sql.ResultSet rs) throws java.sql.SQLException {
        var dto = new TransporterRequestDto(rs.getObject("organization_id", UUID.class), rs.getObject("id", UUID.class),
                rs.getObject("applicant_contact_id", UUID.class), TransporterRequestDto.TypeEnum.fromValue(rs.getInt("type")),
                rs.getInt("status"), rs.getObject("created_at", LocalDateTime.class).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        dto.setTransporterId(rs.getObject("transporter_id", UUID.class));
        dto.setSourceTransporterVersionId(rs.getObject("source_transporter_version_id", UUID.class));
        var executor = rs.getObject("executor_id", UUID.class);
        if (executor != null) dto.executorId(executor);
        var submitted = rs.getObject("submitted_at", LocalDateTime.class);
        if (submitted != null) dto.submittedAt(submitted.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return new Request(dto, rs.getString("pending_action"), rs.getObject("pending_command_id", UUID.class),
                rs.getString("proposed_data"));
    }

    private void requireApplicant(Request request) {
        if (!request.dto().getApplicantContactId().equals(CurrentAccount.id())) forbidden();
    }

    private static void requireState(Request request, int... states) {
        if (request.pendingAction() != null || Arrays.stream(states).noneMatch(state -> state == request.dto().getStatus())) {
            conflict("Действие недоступно в текущем состоянии заявления");
        }
    }

    private void requireRepresentation(UUID org, UUID user) {
        boolean allowed = db.sql("""
                SELECT EXISTS (SELECT 1 FROM transporter_organization_users
                    WHERE organization_id = :org AND user_id = :user AND can_represent)
                """).param("org", org).param("user", user).query(Boolean.class).single();
        if (!allowed) forbidden();
    }

    private boolean hasPermission(UUID user, String permission) {
        return db.sql("""
                SELECT EXISTS (SELECT 1 FROM user_roles ur JOIN role_permission rp ON rp.role_id = ur.role_id
                    WHERE ur.user_id = :user AND rp.permission = :permission)
                """).param("user", user).param("permission", permission).query(Boolean.class).single();
    }

    static String permission(int type, String action) {
        return switch (type) {
            case 1 -> "transporter.registration.";
            case 2 -> "transporter.amendment.";
            case 3 -> "transporter.carrier-cancellation.";
            case 4 -> "transporter.admin-cancellation.";
            default -> throw new IllegalArgumentException("Unknown registration type " + type);
        } + action;
    }

    private static String process(int type) {
        return switch (type) {
            case 1 -> "blm_initial_registration";
            case 2 -> "blm_registration_amendment";
            case 3 -> "blm_carrier_cancellation";
            case 4 -> "blm_admin_cancellation";
            default -> throw new IllegalArgumentException("Unknown registration type " + type);
        };
    }

    private static int type(Request request) {
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

    private record Carrier(UUID organization, int status) {
    }

    record Request(TransporterRequestDto dto, String pendingAction, UUID pendingCommand, String proposedData) {
    }
}
