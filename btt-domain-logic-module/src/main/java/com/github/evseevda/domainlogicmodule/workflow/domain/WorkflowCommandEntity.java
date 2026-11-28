package com.github.evseevda.domainlogicmodule.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;

/** Команда, сохранённая для доставки в Zeebe. */
@Entity
@Getter
@Table(name = "workflow_command")
public class WorkflowCommandEntity {
    @Id private UUID id;
    private UUID requestId;
    private String requestKind = "bpmn";
    private String processId;
    private String messageName;

    @Column(columnDefinition = "text")
    private String variables;

    private LocalDateTime createdAt;
    private LocalDateTime deliveredAt;
    private int attemptCount;
    private LocalDateTime nextAttemptAt;

    protected WorkflowCommandEntity() {}

    /** Создаёт команду, которую можно отправить сразу после подтверждения транзакции. */
    public WorkflowCommandEntity(
            UUID id,
            UUID requestId,
            String processId,
            String messageName,
            String variables,
            LocalDateTime createdAt) {
        this.id = id;
        this.requestId = requestId;
        this.processId = processId;
        this.messageName = messageName;
        this.variables = variables;
        this.createdAt = createdAt;
        this.nextAttemptAt = createdAt;
    }

    /** Сохраняет число попыток и время следующей отправки. */
    public void delayUntil(LocalDateTime nextAttempt) {
        attemptCount++;
        nextAttemptAt = nextAttempt;
    }

    /** Помечает команду доставленной. */
    public void markDelivered(LocalDateTime time) {
        deliveredAt = time;
    }
}
