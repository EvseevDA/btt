package com.github.evseevda.domainlogicmodule.workflow;

import com.github.evseevda.domainlogicmodule.organization.service.OrganizationRequests;
import com.github.evseevda.domainlogicmodule.registration.service.RegistrationRequests;
import io.camunda.client.annotation.Deployment;
import io.camunda.client.annotation.JobWorker;
import io.camunda.client.api.command.ClientException;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.client.api.worker.JobClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Map;

/** Связывает задания технических BPMN с транзакционными обработчиками BTT. */
@Component
@Profile("!mock-api")
@ConditionalOnProperty(name = "camunda.client.enabled", havingValue = "true")
@Deployment(resources = "classpath*:/bpmn/blm_*.bpmn")
public class BttJobWorkers {
    private static final Logger LOG = LoggerFactory.getLogger(BttJobWorkers.class);

    private final OrganizationRequests organizations;
    private final RegistrationRequests registrations;
    private final ObjectMapper json;

    /** Связывает воркеры со Spring-сервисами заявок и сериализацией переменных. */
    public BttJobWorkers(OrganizationRequests organizations, RegistrationRequests registrations, ObjectMapper json) {
        this.organizations = organizations;
        this.registrations = registrations;
        this.json = json;
    }

    /** Подключает организацию и назначает первого представителя. */
    @JobWorker(type = BpmnJobTypes.ORGANIZATION_REGISTRATION_APPROVE, autoComplete = false)
    public void approveOrganization(JobClient client, ActivatedJob job) {
        execute(client, job, organizations);
    }

    /** Сохраняет отказ в подключении организации. */
    @JobWorker(type = BpmnJobTypes.ORGANIZATION_REGISTRATION_REJECT, autoComplete = false)
    public void rejectOrganization(JobClient client, ActivatedJob job) {
        execute(client, job, organizations);
    }

    /** Подтверждает присоединение сотрудника к организации. */
    @JobWorker(type = BpmnJobTypes.ORGANIZATION_MEMBERSHIP_APPROVE, autoComplete = false)
    public void approveMembership(JobClient client, ActivatedJob job) {
        execute(client, job, organizations);
    }

    /** Сохраняет отказ в присоединении сотрудника. */
    @JobWorker(type = BpmnJobTypes.ORGANIZATION_MEMBERSHIP_REJECT, autoComplete = false)
    public void rejectMembership(JobClient client, ActivatedJob job) {
        execute(client, job, organizations);
    }

    /** Сохраняет назначение исполнителя. */
    @JobWorker(type = BpmnJobTypes.REGISTRATION_ASSIGNMENT, autoComplete = false)
    public void assignExecutor(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** Передаёт сохранённую редакцию решения на согласование. */
    @JobWorker(type = BpmnJobTypes.REGISTRATION_DECISION, autoComplete = false)
    public void saveDecision(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** Возвращает решение исполнителю. */
    @JobWorker(type = BpmnJobTypes.REGISTRATION_RETURN, autoComplete = false)
    public void returnDecision(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** Применяет отзыв заявления. */
    @JobWorker(type = BpmnJobTypes.REGISTRATION_WITHDRAW, autoComplete = false)
    public void withdrawRequest(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** Применяет согласованный отказ. */
    @JobWorker(type = BpmnJobTypes.REGISTRATION_REFUSAL, autoComplete = false)
    public void finalizeRefusal(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** При положительном решении создаёт перевозчика и первое РУ. */
    @JobWorker(type = BpmnJobTypes.INITIAL_REGISTRATION, autoComplete = false)
    public void applyInitialRegistration(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** Сохраняет новые версии перевозчика и РУ. */
    @JobWorker(type = BpmnJobTypes.REGISTRATION_AMENDMENT, autoComplete = false)
    public void applyAmendment(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** Фиксирует отмену регистрации по инициативе перевозчика. */
    @JobWorker(type = BpmnJobTypes.CARRIER_CANCELLATION, autoComplete = false)
    public void applyCarrierCancellation(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    /** Фиксирует отмену регистрации по инициативе администрации. */
    @JobWorker(type = BpmnJobTypes.ADMIN_CANCELLATION, autoComplete = false)
    public void applyAdminCancellation(JobClient client, ActivatedJob job) {
        execute(client, job, registrations);
    }

    private void execute(JobClient client, ActivatedJob job, WorkflowJobHandler handler) {
        Map<String, ?> result;
        try {
            result = handler.handle(job.getType(), json.readTree(job.getVariables()));
        } catch (RuntimeException ex) {
            LOG.error("Zeebe job {} ({}) failed", job.getKey(), job.getType(), ex);
            client.newFailCommand(job).retries(Math.max(0, job.getRetries() - 1))
                    .retryBackoff(Duration.ofSeconds(5)).errorMessage("BTT workflow job failed: " + job.getType())
                    .send().join();
            return;
        }

        // An unacknowledged completion may already have succeeded. Let the activation expire.
        try {
            client.newCompleteCommand(job).variables(json.writeValueAsString(result)).send().join();
        } catch (ClientException ex) {
            LOG.warn("Completion for Zeebe job {} was not acknowledged ({})", job.getKey(), ex.getClass().getSimpleName());
        }
    }
}
