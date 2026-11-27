package com.github.evseevda.domainlogicmodule.integration;

import com.github.evseevda.domainlogicmodule.organization.domain.OrganizationRequestStatus;
import com.github.evseevda.domainlogicmodule.workflow.BpmnEventNames;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.cloud.discovery.enabled=false",
        "camunda.client.enabled=true",
        "camunda.client.auth.method=none",
        "camunda.client.prefer-rest-over-grpc=false",
        "btt.zeebe.poll-delay-ms=100"
})
@EnabledIfEnvironmentVariable(named = "BTT_ZEEBE_TEST_GRPC_ADDRESS", matches = "https?://.+")
@EnabledIfEnvironmentVariable(named = "BTT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class ZeebeWorkflowIntegrationTest {
    private static final String PASSWORD = "integration-password-123";
    private static final Duration WORKFLOW_TIMEOUT = Duration.ofSeconds(30);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcClient db;
    @Autowired
    private ObjectMapper json;

    @DynamicPropertySource
    static void connections(DynamicPropertyRegistry registry) {
        BackendIntegrationTest.dataSource(registry);
        registry.add("camunda.client.grpc-address", () -> System.getenv("BTT_ZEEBE_TEST_GRPC_ADDRESS"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void organizationRegistrationWaitsForDecisionAndAppliesItThroughZeebe(boolean approved) throws Exception {
        String applicant = account(false);
        String reviewer = account(true);
        String digits = String.format(Locale.ROOT, "%08d", ThreadLocalRandom.current().nextInt(100_000_000));
        String inn = "52" + digits;
        var request = send(201, "/organization-registrations", applicant, Map.of(
                "name", "Zeebe Integration Carrier", "inn", inn, "ogrn", "10252" + digits,
                "legalAddress", "Test address", "authorityEvidence", "Test authorization"));
        UUID requestId = UUID.fromString(request.path("id").asText());
        String path = "/organization-requests/" + requestId;

        await(() -> delivered(requestId, false), "Zeebe did not acknowledge process creation for " + requestId);
        assertEquals(OrganizationRequestStatus.PENDING.getId(), send(200, path, applicant, null).path("status").asInt());

        String rejectionReason = "No authorization";
        var decision = approved ? Map.of("approved", true) : Map.of("approved", false, "comment", rejectionReason);
        send(202, path + "/decision", reviewer, decision);
        await(() -> delivered(requestId, true), "Zeebe did not acknowledge publication for " + requestId);
        var expected = approved ? OrganizationRequestStatus.APPROVED : OrganizationRequestStatus.REJECTED;
        await(() -> send(200, path, applicant, null).path("status").asInt() == expected.getId(),
                "Zeebe worker did not apply " + expected + " for " + requestId);

        var result = send(200, path, applicant, null);
        var organizations = db.sql("SELECT id FROM transporter_organization WHERE inn = :inn")
                .param("inn", inn).query(UUID.class).list();
        if (approved) {
            UUID organizationId = UUID.fromString(result.path("organizationId").asText());
            assertEquals(java.util.List.of(organizationId), organizations);
        } else {
            assertFalse(result.hasNonNull("organizationId"));
            assertEquals(rejectionReason, result.path("decisionComment").asText());
            assertTrue(organizations.isEmpty());
        }
    }

    private boolean delivered(UUID requestId, boolean message) {
        return db.sql("""
                SELECT EXISTS (SELECT 1 FROM workflow_command WHERE request_id = :requestId
                    AND delivered_at IS NOT NULL AND
                    ((:message = true AND message_name = :event) OR
                     (:message = false AND process_id = 'blm_organization_registration')))
                """).param("requestId", requestId).param("message", message)
                .param("event", BpmnEventNames.ORGANIZATION_REGISTRATION_DECISION_SUBMITTED).query(Boolean.class).single();
    }

    private String account(boolean reviewer) throws Exception {
        String username = "zeebe-" + UUID.randomUUID();
        var user = send(201, "/users", null, Map.of("username", username, "email", username + "@example.test",
                "fullName", "Workflow Integration User", "password", PASSWORD));
        if (reviewer) {
            assertEquals(1, db.sql("INSERT INTO user_roles (user_id, role_id) SELECT :id, id FROM role WHERE name = 'PORTAL_ADMIN'")
                    .param("id", UUID.fromString(user.path("id").asText())).update());
        }
        return send(200, "/auth/token", null, Map.of("username", username, "password", PASSWORD)).path("token").asText();
    }

    private JsonNode send(int status, String path, String token, Map<String, ?> body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(5));
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (body == null) {
            request.GET();
        } else {
            request.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        }
        var response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), () -> path + ": " + response.body());
        return json.readTree(response.body());
    }

    private static void await(WorkflowCheck check, String failureMessage) throws Exception {
        long deadline = System.nanoTime() + WORKFLOW_TIMEOUT.toNanos();
        do {
            if (check.satisfied()) return;
            Thread.sleep(100);
        } while (System.nanoTime() < deadline);
        fail(failureMessage);
    }

    @FunctionalInterface
    private interface WorkflowCheck {
        boolean satisfied() throws Exception;
    }
}
