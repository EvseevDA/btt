package com.github.evseevda.domainlogicmodule.workflow;

import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowCommandRepository;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/** Сохраняет команды запуска и сообщения Zeebe в транзакционном журнале отправки. */
@Component
@Profile("!mock-api")
public class WorkflowCommands {
    private final WorkflowCommandRepository repository;
    private final ObjectMapper json;

    /** Создаёт журнал команд с доступом к базе и JSON-сериализацией. */
    public WorkflowCommands(WorkflowCommandRepository repository, ObjectMapper json) {
        this.repository = repository;
        this.json = json;
    }

    /** Добавляет запуск процесса по BPMN ID с идентификатором заявки. */
    public void enqueueProcessStart(UUID id, String process) {
        saveWorkflowCommand(UUID.randomUUID(), id, process, null, Map.of());
    }

    /** Добавляет сообщение с ключом корреляции заявки в журнал отправки. */
    public void enqueueMessage(UUID id, String name, Map<String, ?> payload) {
        enqueueMessage(UUID.randomUUID(), id, name, payload);
    }

    /** Добавляет сообщение с ключом корреляции заявки в журнал отправки. */
    public void enqueueMessage(UUID command, UUID request, String name, Map<String, ?> payload) {
        saveWorkflowCommand(command, request, null, name, payload);
    }

    private void saveWorkflowCommand(
            UUID command, UUID id, String process, String message, Map<String, ?> payload) {
        var variables = new HashMap<String, Object>(payload);
        variables.put("requestId", id.toString());
        variables.put("commandId", command.toString());
        repository.insertCommand(command, id, process, message, json.writeValueAsString(variables));
    }
}
