package com.github.evseevda.domainlogicmodule.workflow.repository;

import com.github.evseevda.domainlogicmodule.workflow.domain.WorkflowCommandEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Блокирует и отмечает доставку команд Camunda с сохранением порядка для каждой заявки. */
public interface WorkflowDispatchRepository
        extends JpaRepository<WorkflowCommandEntity, UUID>, WorkflowDispatchRepositoryCustom {
    /** Данные команды, необходимые для вызова клиента Camunda. */
    record PendingWorkflowCommand(
            UUID id,
            UUID request,
            String process,
            String message,
            String variables,
            int attempts) {}
}
