package com.github.evseevda.domainlogicmodule.workflow.repository;

import com.github.evseevda.domainlogicmodule.workflow.domain.WorkflowCommandEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Записывает команды Camunda в транзакции, создающей или изменяющей заявку. */
public interface WorkflowCommandRepository
        extends JpaRepository<WorkflowCommandEntity, UUID>, WorkflowCommandRepositoryCustom {}
