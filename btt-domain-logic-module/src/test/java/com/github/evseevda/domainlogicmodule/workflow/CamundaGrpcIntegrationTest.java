package com.github.evseevda.domainlogicmodule.workflow;

import com.github.evseevda.domainlogicmodule.organization.service.OrganizationRequests;
import com.github.evseevda.domainlogicmodule.registration.service.RegistrationRequests;
import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository;
import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository.Command;
import io.camunda.client.CamundaClient;
import io.camunda.client.api.response.ActivatedJob;
import io.camunda.zeebe.gateway.protocol.GatewayGrpc;
import io.camunda.zeebe.gateway.protocol.GatewayOuterClass.*;
import io.grpc.Server;
import io.grpc.Status;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.net.InetSocketAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CamundaGrpcIntegrationTest {
    private final Gateway gateway = new Gateway();
    private final ObjectMapper json = new JsonMapper();
    private Server server;
    private CamundaClient client;

    @BeforeEach
    void startGateway() throws Exception {
        server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
                .addService(gateway).build().start();
        client = CamundaClient.newClientBuilder()
                .applyEnvironmentVariableOverrides(false)
                .grpcAddress(URI.create("http://127.0.0.1:" + server.getPort()))
                .preferRestOverGrpc(false)
                .defaultRequestTimeout(Duration.ofSeconds(5))
                .build();
    }

    @AfterEach
    void closeGateway() throws Exception {
        try {
            if (client != null) {
                client.close();
            }
        } finally {
            if (server != null) {
                server.shutdownNow().awaitTermination(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void dispatchCreatesLatestProcessAndPublishesCorrelatedMessageWithStableId() {
        var repository = mock(WorkflowDispatchRepository.class);
        var requestId = UUID.randomUUID();
        var start = new Command(UUID.randomUUID(), requestId, "blm_organization_registration", null,
                json.writeValueAsString(Map.of("requestId", requestId)), 0);
        var message = new Command(UUID.randomUUID(), requestId, null, BpmnEventNames.ORGANIZATION_REGISTRATION_DECISION_SUBMITTED,
                start.variables(), 0);
        when(repository.lockNextCommand()).thenReturn(Optional.of(start), Optional.of(message), Optional.empty());

        dispatcher(repository).poll();

        var created = gateway.starts.remove();
        assertEquals(start.process(), created.getBpmnProcessId());
        assertEquals(-1, created.getVersion());
        assertEquals(requestId.toString(), json.readTree(created.getVariables()).path("requestId").asText());
        var published = gateway.messages.remove();
        assertEquals(message.message(), published.getName());
        assertEquals(requestId.toString(), published.getCorrelationKey());
        assertEquals(message.id().toString(), published.getMessageId());
        assertEquals(Duration.ofDays(1).toMillis(), published.getTimeToLive());
        assertEquals(json.readTree(start.variables()), json.readTree(published.getVariables()));
        var deliveries = inOrder(repository);
        deliveries.verify(repository).markDelivered(start.id());
        deliveries.verify(repository).markDelivered(message.id());
    }

    @Test
    void rejectedGrpcMessageSchedulesRetryWithoutMarkingDelivered() {
        var repository = mock(WorkflowDispatchRepository.class);
        var command = new Command(UUID.randomUUID(), UUID.randomUUID(), null, "rejected", "{}", 3);
        when(repository.lockNextCommand()).thenReturn(Optional.of(command), Optional.empty());

        dispatcher(repository).poll();

        assertEquals(command.id().toString(), gateway.messages.remove().getMessageId());
        verify(repository).delayRetry(8, command.id());
        verify(repository, never()).markDelivered(any());
    }

    @Test
    void workerSendsCompletionWithHandlerResult() {
        var organizations = mock(OrganizationRequests.class);
        var registrations = mock(RegistrationRequests.class);
        var job = job(BpmnJobTypes.REGISTRATION_DECISION);
        when(registrations.handle(job.getType(), json.readTree(job.getVariables())))
                .thenAnswer(invocation -> Map.of("decisionType", 1));

        new BttJobWorkers(organizations, registrations, json).saveDecision(client, job);

        var completed = gateway.completed.remove();
        assertEquals(job.getKey(), completed.getJobKey());
        assertEquals(1, json.readTree(completed.getVariables()).path("decisionType").asInt());
        assertTrue(gateway.failed.isEmpty());
    }

    @Test
    void workerFailureDecrementsRetriesAndDoesNotCompleteJob() {
        var organizations = mock(OrganizationRequests.class);
        var registrations = mock(RegistrationRequests.class);
        var job = job(BpmnJobTypes.ORGANIZATION_REGISTRATION_APPROVE);
        when(organizations.handle(job.getType(), json.readTree(job.getVariables())))
                .thenThrow(new IllegalStateException("test handler failure"));

        new BttJobWorkers(organizations, registrations, json).approveOrganization(client, job);

        var failed = gateway.failed.remove();
        assertEquals(job.getKey(), failed.getJobKey());
        assertEquals(job.getRetries() - 1, failed.getRetries());
        assertEquals(Duration.ofSeconds(5).toMillis(), failed.getRetryBackOff());
        assertTrue(failed.getErrorMessage().contains(job.getType()));
        assertTrue(gateway.completed.isEmpty());
    }

    private ZeebeRuntime dispatcher(WorkflowDispatchRepository repository) {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(invocation -> new SimpleTransactionStatus());
        return new ZeebeRuntime(client, repository, new TransactionTemplate(manager));
    }

    private ActivatedJob job(String type) {
        var job = mock(ActivatedJob.class);
        when(job.getType()).thenReturn(type);
        when(job.getKey()).thenReturn(42L);
        when(job.getVariables()).thenReturn("{\"requestId\":\"test\"}");
        when(job.getRetries()).thenReturn(3);
        return job;
    }

    private static final class Gateway extends GatewayGrpc.GatewayImplBase {
        private final BlockingQueue<CreateProcessInstanceRequest> starts = new LinkedBlockingQueue<>();
        private final BlockingQueue<PublishMessageRequest> messages = new LinkedBlockingQueue<>();
        private final BlockingQueue<CompleteJobRequest> completed = new LinkedBlockingQueue<>();
        private final BlockingQueue<FailJobRequest> failed = new LinkedBlockingQueue<>();

        @Override
        public void createProcessInstance(CreateProcessInstanceRequest request, StreamObserver<CreateProcessInstanceResponse> response) {
            starts.add(request);
            reply(response, CreateProcessInstanceResponse.getDefaultInstance());
        }

        @Override
        public void publishMessage(PublishMessageRequest request, StreamObserver<PublishMessageResponse> response) {
            messages.add(request);
            if ("rejected".equals(request.getName())) {
                response.onError(Status.INVALID_ARGUMENT.withDescription("test rejection").asRuntimeException());
                return;
            }
            reply(response, PublishMessageResponse.getDefaultInstance());
        }

        @Override
        public void completeJob(CompleteJobRequest request, StreamObserver<CompleteJobResponse> response) {
            completed.add(request);
            reply(response, CompleteJobResponse.getDefaultInstance());
        }

        @Override
        public void failJob(FailJobRequest request, StreamObserver<FailJobResponse> response) {
            failed.add(request);
            reply(response, FailJobResponse.getDefaultInstance());
        }

        private static <T> void reply(StreamObserver<T> response, T value) {
            response.onNext(value);
            response.onCompleted();
        }
    }
}
