package com.github.evseevda.domainlogicmodule.workflow.repository;

import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.workflow.domain.WorkflowCommandEntity;
import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository.PendingWorkflowCommand;
import jakarta.persistence.EntityManager;
import java.util.Optional;
import java.util.UUID;
import org.hibernate.LockMode;
import org.hibernate.query.SelectionQuery;

/** Блокирует и отмечает доставку команд Camunda с сохранением порядка для каждой заявки. */
class WorkflowDispatchRepositoryCustomImpl extends JpaQueries
        implements WorkflowDispatchRepositoryCustom {
    /** Подключает EntityManager для транзакционного выбора команд. */
    WorkflowDispatchRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Блокирует первую готовую команду и пропускает строки, заблокированные другим диспетчером. */
    @Override
    public Optional<PendingWorkflowCommand> findNextCommandWithLock() {
        var query =
                createTypedQuery(
                                """
                select w from WorkflowCommandEntity w
                where w.deliveredAt is null and w.nextAttemptAt <= :now
                    and not exists (select earlier.id from WorkflowCommandEntity earlier
                        where earlier.requestId = w.requestId and earlier.deliveredAt is null
                          and (earlier.createdAt < w.createdAt
                            or (earlier.createdAt = w.createdAt and earlier.id < w.id)))
                order by w.createdAt, w.id
                """,
                                WorkflowCommandEntity.class)
                        .setParameter("now", getCurrentUtcDateTime())
                        .setMaxResults(1);
        query.unwrap(SelectionQuery.class).setHibernateLockMode(LockMode.UPGRADE_SKIPLOCKED);
        return findSingleResult(query)
                .map(
                        command ->
                                new PendingWorkflowCommand(
                                        command.getId(),
                                        command.getRequestId(),
                                        command.getProcessId(),
                                        command.getMessageName(),
                                        command.getVariables(),
                                        command.getAttemptCount()));
    }

    /** Откладывает повторную отправку команды. */
    @Override
    public void scheduleCommandRetry(int delay, UUID id) {
        getReference(WorkflowCommandEntity.class, id)
                .delayUntil(getCurrentUtcDateTime().plusSeconds(delay));
    }

    /** Отмечает успешно доставленную команду. */
    @Override
    public void markCommandDelivered(UUID id) {
        getReference(WorkflowCommandEntity.class, id).markDelivered(getCurrentUtcDateTime());
    }
}
