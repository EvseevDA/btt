package com.github.evseevda.domainlogicmodule.workflow.repository;

import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository.Command;
import java.util.Optional;
import java.util.UUID;

/** Дополнительные операции репозитория. */
interface WorkflowDispatchRepositoryCustom {
    /** Блокирует первую готовую команду и пропускает строки, заблокированные другим диспетчером. */
    Optional<Command> lockNextCommand();

    /** Откладывает повторную отправку команды. */
    void delayRetry(int delay, UUID id);

    /** Отмечает успешно доставленную команду. */
    void markDelivered(UUID id);
}
