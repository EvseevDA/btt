package com.github.evseevda.domainlogicmodule.workflow;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;

/** Отправляет сохранённые команды Zeebe через Camunda Java Client. */
@Component
@Profile("!mock-api")
@EnableScheduling
@ConditionalOnProperty(name = "camunda.client.enabled", havingValue = "true")
public class ZeebeRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(ZeebeRuntime.class);

    private final CamundaClient client;
    private final WorkflowDispatchRepository repository;
    private final TransactionTemplate transactions;

    /** Создаёт диспетчер с клиентом из Camunda Spring Boot Starter. */
    public ZeebeRuntime(CamundaClient client, WorkflowDispatchRepository repository, TransactionTemplate transactions) {
        this.client = client;
        this.repository = repository;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelayString = "${btt.zeebe.poll-delay-ms:1000}")
    void poll() {
        for (int i = 0; i < 20; i++) {
            if (!Boolean.TRUE.equals(transactions.execute(status -> dispatch()))) {
                break;
            }
        }
    }

    private boolean dispatch() {
        var next = repository.lockNextCommand();
        if (next.isEmpty()) return false;
        var command = next.orElseThrow();
        try {
            if (command.process() != null) {
                client.newCreateInstanceCommand().bpmnProcessId(command.process()).latestVersion()
                        .variables(command.variables()).send().join();
            } else {
                client.newPublishMessageCommand().messageName(command.message())
                        .correlationKey(command.request().toString()).messageId(command.id().toString())
                        .timeToLive(Duration.ofDays(1)).variables(command.variables()).send().join();
            }
        } catch (ClientException ex) {
            int delay = 1 << Math.min(8, command.attempts());
            repository.delayRetry(delay, command.id());
            LOG.warn("Zeebe command {} not acknowledged. Retry in {}s ({})",
                    command.id(), delay, ex.getClass().getSimpleName());
            return true;
        }
        repository.markDelivered(command.id());
        return true;
    }

}
