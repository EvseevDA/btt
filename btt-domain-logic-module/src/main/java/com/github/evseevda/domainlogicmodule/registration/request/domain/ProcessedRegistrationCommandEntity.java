package com.github.evseevda.domainlogicmodule.registration.request.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;

/** Результат выполненной команды, который можно вернуть при её повторной доставке. */
@Entity
@Getter
@Table(name = "registration_processed_command")
public class ProcessedRegistrationCommandEntity {
    @Id private UUID id;
    private UUID requestId;
    private Integer decisionType;

    protected ProcessedRegistrationCommandEntity() {}

    /** Сохраняет результат команды для конкретного заявления. */
    public ProcessedRegistrationCommandEntity(UUID id, UUID requestId, Integer decisionType) {
        this.id = id;
        this.requestId = requestId;
        this.decisionType = decisionType;
    }
}
