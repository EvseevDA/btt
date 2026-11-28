package com.github.evseevda.domainlogicmodule.workflow;

import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Отправляет сохранённые команды Zeebe через Camunda Java Client. */
@Component
@Profile("!mock-api")
@EnableScheduling
@ConditionalOnProperty(name = "camunda.client.enabled", havingValue = "true")
@Slf4j
public class WorkflowDispatchService {

    private final CamundaClient client;
    private final WorkflowDispatchRepository repository;
    private final TransactionTemplate transactions;

    /** Создаёт диспетчер с клиентом из Camunda Spring Boot Starter. */
    public WorkflowDispatchService(
            CamundaClient client,
            WorkflowDispatchRepository repository,
            TransactionTemplate transactions) {
        this.client = client;
        this.repository = repository;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${btt.zeebe.poll-delay-ms:1000}")
    void dispatchPendingCommands() {
        for (int i = 0; i < 20; i++) {
            if (!Boolean.TRUE.equals(transactions.execute(status -> dispatchNextCommand()))) {
                break;
            }
        }
    }

    private boolean dispatchNextCommand() {
        var next = repository.findNextCommandWithLock();
        if (next.isEmpty()) {
            return false;
        }
        var command = next.orElseThrow();
        try {
            if (command.process() != null) {
                client.newCreateInstanceCommand()
                        .bpmnProcessId(command.process())
                        .latestVersion()
                        .variables(command.variables())
                        .send()
                        .join();
            } else {
                client.newPublishMessageCommand()
                        .messageName(command.message())
                        .correlationKey(command.request().toString())
                        .messageId(command.id().toString())
                        .timeToLive(Duration.ofDays(1))
                        .variables(command.variables())
                        .send()
                        .join();
            }
        } catch (ClientException ex) {
            int delay = 1 << Math.min(8, command.attempts());
            repository.scheduleCommandRetry(delay, command.id());
            log.warn(
                    "Zeebe command {} not acknowledged. Retry in {}s ({})",
                    command.id(),
                    delay,
                    ex.getClass().getSimpleName());
            return true;
        }
        repository.markCommandDelivered(command.id());
        return true;
    }
}
