package com.github.evseevda.domainlogicmodule.workflow;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Сохраняет команды запуска и сообщения Zeebe в транзакционном журнале отправки. */
@Component
@Profile("!mock-api")
public class WorkflowCommands {
    private final JdbcClient db;
    private final ObjectMapper json;

    /** Создаёт журнал команд с доступом к базе и JSON-сериализацией. */
    public WorkflowCommands(JdbcClient db, ObjectMapper json) {
        this.db = db;
        this.json = json;
    }

    /** Добавляет запуск процесса по BPMN ID с идентификатором заявки. */
    public void start(UUID id, String process) {
        enqueue(UUID.randomUUID(), id, process, null, Map.of());
    }

    /** Добавляет сообщение с ключом корреляции заявки в журнал отправки. */
    public void message(UUID id, String name, Map<String, ?> payload) {
        message(UUID.randomUUID(), id, name, payload);
    }

    /** Добавляет сообщение с ключом корреляции заявки в журнал отправки. */
    public void message(UUID command, UUID request, String name, Map<String, ?> payload) {
        enqueue(command, request, null, name, payload);
    }

    private void enqueue(UUID command, UUID id, String process, String message, Map<String, ?> payload) {
        var variables = new HashMap<String, Object>(payload);
        variables.put("requestId", id.toString());
        variables.put("commandId", command.toString());
        db.sql("""
                INSERT INTO workflow_command (id, request_id, request_kind, process_id, message_name, variables, created_at)
                VALUES (:id, :request, 'bpmn', :process, :message, :variables, timezone('UTC', clock_timestamp()))
                """).param("id", command).param("request", id).param("process", process)
                .param("message", message).param("variables", json.writeValueAsString(variables)).update();
    }
}
