package com.github.evseevda.domainlogicmodule.workflow.repository;

import java.util.UUID;

/** Дополнительные операции репозитория. */
interface WorkflowCommandRepositoryCustom {
    /** Добавляет команду запуска или публикации сообщения. */
    void enqueue(UUID command, UUID id, String process, String message, String variables);
}
