package com.github.evseevda.domainlogicmodule.workflow;

import java.util.Map;
import tools.jackson.databind.JsonNode;

/** Контракт сервиса, обрабатывающего задания Zeebe с переменными процесса. */
@FunctionalInterface
public interface WorkflowJobHandler {
    /** Выполняет задание Zeebe и возвращает переменные для продолжения процесса. */
    Map<String, ?> handleJob(String type, JsonNode variables);
}
