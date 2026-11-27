package com.github.evseevda.domainlogicmodule.workflow.repository;

import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.workflow.domain.WorkflowCommand;
import jakarta.persistence.EntityManager;

import java.util.UUID;

/** Записывает команды Camunda в транзакции, создающей или изменяющей заявку. */
class WorkflowCommandRepositoryCustomImpl extends JpaQueries implements WorkflowCommandRepositoryCustom {
    /** Подключает EntityManager для записи команд. */
    WorkflowCommandRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Добавляет команду запуска или публикации сообщения. */
    @Override
    public void enqueue(UUID command, UUID id, String process, String message, String variables) {
        em.persist(new WorkflowCommand(command, id, process, message, variables, now()));
    }
}
