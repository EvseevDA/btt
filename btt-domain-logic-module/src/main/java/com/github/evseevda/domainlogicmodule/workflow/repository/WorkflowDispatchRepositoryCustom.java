package com.github.evseevda.domainlogicmodule.workflow.repository;

import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository.PendingWorkflowCommand;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface WorkflowDispatchRepositoryCustom {
    /** Блокирует первую готовую команду и пропускает строки, заблокированные другим диспетчером. */
    Optional<PendingWorkflowCommand> findNextCommandWithLock();

    /** Откладывает повторную отправку команды. */
    void scheduleCommandRetry(int delay, UUID id);

    /** Отмечает успешно доставленную команду. */
    void markCommandDelivered(UUID id);
}
