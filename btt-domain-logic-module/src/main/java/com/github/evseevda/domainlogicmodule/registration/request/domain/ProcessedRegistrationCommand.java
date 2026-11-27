package com.github.evseevda.domainlogicmodule.registration.request.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;

import java.util.UUID;

/** Результат выполненной команды, который можно вернуть при её повторной доставке. */
@Entity
@Getter
@Table(name = "registration_processed_command")
public class ProcessedRegistrationCommand {
    @Id private UUID id;
    private UUID requestId;
    private Integer decisionType;

    protected ProcessedRegistrationCommand() {
    }

    /** Сохраняет результат команды для конкретного заявления. */
    public ProcessedRegistrationCommand(UUID id, UUID requestId, Integer decisionType) {
        this.id = id;
        this.requestId = requestId;
        this.decisionType = decisionType;
    }
}
