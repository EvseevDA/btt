package com.github.evseevda.domainlogicmodule.workflow;

import tools.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Set;

/** Контракт сервиса, обрабатывающего задания Zeebe определённых типов. */
public interface WorkflowJobHandler {
    /** Возвращает типы заданий, поддерживаемые этим обработчиком. */
    Set<String> jobTypes();

    /** Выполняет задание Zeebe и возвращает переменные для продолжения процесса. */
    Map<String, ?> handle(String type, JsonNode variables);
}
