package com.github.evseevda.domainlogicmodule.workflow;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.command.ClientException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.UUID;

/** Отправляет сохранённые команды Zeebe через Camunda Java Client. */
@Component
@Profile("!mock-api")
@EnableScheduling
@ConditionalOnProperty(name = "camunda.client.enabled", havingValue = "true")
public class ZeebeRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(ZeebeRuntime.class);

    private final CamundaClient client;
    private final JdbcClient db;
    private final TransactionTemplate transactions;

    /** Создаёт диспетчер с клиентом из Camunda Spring Boot Starter. */
    public ZeebeRuntime(CamundaClient client, JdbcClient db, TransactionTemplate transactions) {
        this.client = client;
        this.db = db;
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
        var next = db.sql("""
                SELECT w.id, w.request_id, w.process_id, w.message_name, w.variables, w.attempt_count
                FROM workflow_command w
                WHERE w.delivered_at IS NULL AND w.next_attempt_at <= timezone('UTC', clock_timestamp())
                  AND NOT EXISTS (SELECT 1 FROM workflow_command earlier
                      WHERE earlier.request_id = w.request_id AND earlier.delivered_at IS NULL
                        AND (earlier.created_at, earlier.id) < (w.created_at, w.id))
                ORDER BY w.created_at, w.id LIMIT 1 FOR UPDATE OF w SKIP LOCKED
                """).query((rs, row) -> new Command(rs.getObject("id", UUID.class),
                        rs.getObject("request_id", UUID.class), rs.getString("process_id"),
                        rs.getString("message_name"), rs.getString("variables"),
                        rs.getInt("attempt_count"))).optional();
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
            db.sql("""
                    UPDATE workflow_command SET attempt_count = attempt_count + 1,
                        next_attempt_at = timezone('UTC', clock_timestamp()) + :delay * interval '1 second'
                    WHERE id = :id
                    """).param("delay", delay).param("id", command.id()).update();
            LOG.warn("Zeebe command {} not acknowledged. Retry in {}s ({})",
                    command.id(), delay, ex.getClass().getSimpleName());
            return true;
        }
        db.sql("UPDATE workflow_command SET delivered_at = timezone('UTC', clock_timestamp()) WHERE id = :id")
                .param("id", command.id()).update();
        return true;
    }

    private record Command(UUID id, UUID request, String process, String message, String variables, int attempts) {
    }
}
