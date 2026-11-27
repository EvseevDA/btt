package com.github.evseevda.domainlogicmodule.workflow.repository;

import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository.Command;
import com.github.evseevda.domainlogicmodule.persistence.JpaQueries;
import com.github.evseevda.domainlogicmodule.workflow.domain.WorkflowCommand;
import jakarta.persistence.EntityManager;
import org.hibernate.LockMode;
import org.hibernate.query.SelectionQuery;

import java.util.Optional;
import java.util.UUID;

/** Блокирует и отмечает доставку команд Camunda с сохранением порядка для каждой заявки. */
class WorkflowDispatchRepositoryCustomImpl extends JpaQueries implements WorkflowDispatchRepositoryCustom {
    /** Подключает EntityManager для транзакционного выбора команд. */
    WorkflowDispatchRepositoryCustomImpl(EntityManager em) {
        super(em);
    }

    /** Блокирует первую готовую команду и пропускает строки, заблокированные другим диспетчером. */
    @Override
    public Optional<Command> lockNextCommand() {
        var query = select("""
                select w from WorkflowCommand w
                where w.deliveredAt is null and w.nextAttemptAt <= :now
                    and not exists (select earlier.id from WorkflowCommand earlier
                        where earlier.requestId = w.requestId and earlier.deliveredAt is null
                          and (earlier.createdAt < w.createdAt
                            or (earlier.createdAt = w.createdAt and earlier.id < w.id)))
                order by w.createdAt, w.id
                """, WorkflowCommand.class).setParameter("now", now()).setMaxResults(1);
        query.unwrap(SelectionQuery.class).setHibernateLockMode(LockMode.UPGRADE_SKIPLOCKED);
        return one(query).map(command -> new Command(command.getId(), command.getRequestId(),
                command.getProcessId(), command.getMessageName(), command.getVariables(), command.getAttemptCount()));
    }

    /** Откладывает повторную отправку команды. */
    @Override
    public void delayRetry(int delay, UUID id) {
        ref(WorkflowCommand.class, id).delayUntil(now().plusSeconds(delay));
    }

    /** Отмечает успешно доставленную команду. */
    @Override
    public void markDelivered(UUID id) {
        ref(WorkflowCommand.class, id).markDelivered(now());
    }

}
