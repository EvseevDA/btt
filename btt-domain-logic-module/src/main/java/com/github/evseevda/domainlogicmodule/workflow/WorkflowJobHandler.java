package com.github.evseevda.domainlogicmodule.workflow;

import tools.jackson.databind.JsonNode;

import java.util.Map;

/** Контракт сервиса, обрабатывающего задания Zeebe с переменными процесса. */
@FunctionalInterface
public interface WorkflowJobHandler {
    /** Выполняет задание Zeebe и возвращает переменные для продолжения процесса. */
    Map<String, ?> handle(String type, JsonNode variables);
}
