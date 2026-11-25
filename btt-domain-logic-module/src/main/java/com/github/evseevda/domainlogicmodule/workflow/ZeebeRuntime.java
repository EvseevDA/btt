package com.github.evseevda.domainlogicmodule.workflow;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Profile("!mock-api")
@EnableScheduling
@ConditionalOnProperty(name = "btt.zeebe.url")
public class ZeebeRuntime {
    private static final Logger LOG = LoggerFactory.getLogger(ZeebeRuntime.class);

    private final RestClient client;
    private final JdbcClient db;
    private final TransactionTemplate transactions;
    private final ObjectMapper json;
    private final Map<String, WorkflowJobHandler> handlers = new LinkedHashMap<>();
    private boolean deployed;

    public ZeebeRuntime(@Value("${btt.zeebe.url}") String url,
                        @Value("${btt.zeebe.token:}") String token,
                        JdbcClient db, TransactionTemplate transactions, ObjectMapper json,
                        List<WorkflowJobHandler> jobHandlers) {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        var builder = RestClient.builder().baseUrl(url).requestFactory(factory);
        if (!token.isBlank()) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        client = builder.build();
        this.db = db;
        this.transactions = transactions;
        this.json = json;
        for (var handler : jobHandlers) {
            for (var type : handler.jobTypes()) {
                if (handlers.putIfAbsent(type, handler) != null) {
                    throw new IllegalStateException("Duplicate Zeebe job handler: " + type);
                }
            }
        }
    }

    @Scheduled(fixedDelayString = "${btt.zeebe.poll-delay-ms:1000}")
    void poll() throws IOException {
        if (!deployed) {
            deploy();
            deployed = true;
        }
        for (int i = 0; i < 20; i++) {
            if (!Boolean.TRUE.equals(transactions.execute(status -> dispatch()))) break;
        }
        handlers.forEach(this::pollJobs);
    }

    private void deploy() throws IOException {
        var parts = new LinkedMultiValueMap<String, Object>();
        for (var resource : new PathMatchingResourcePatternResolver().getResources("classpath*:bpmn/blm_*.bpmn")) {
            parts.add("resources", resource);
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException("No BTT BPMN resources");
        }
        client.post().uri("/v2/deployments").contentType(MediaType.MULTIPART_FORM_DATA)
                .body(parts).retrieve().toBodilessEntity();
    }

    private boolean dispatch() {
        var next = db.sql("""
                SELECT id, request_id, process_id, message_name, variables FROM workflow_command
                WHERE delivered_at IS NULL ORDER BY created_at, id LIMIT 1 FOR UPDATE SKIP LOCKED
                """).query((rs, row) -> new Command(rs.getObject("id", UUID.class),
                        rs.getObject("request_id", UUID.class), rs.getString("process_id"),
                        rs.getString("message_name"), json.readTree(rs.getString("variables")))).optional();
        if (next.isEmpty()) return false;
        var command = next.orElseThrow();
        if (command.process() != null) {
            post("/v2/process-instances", Map.of("processDefinitionId", command.process(), "variables", command.variables()));
        } else {
            post("/v2/messages/publication", Map.of("name", command.message(), "correlationKey", command.request().toString(),
                    "messageId", command.id().toString(), "timeToLive", 86_400_000, "variables", command.variables()));
        }
        db.sql("UPDATE workflow_command SET delivered_at = timezone('UTC', clock_timestamp()) WHERE id = :id")
                .param("id", command.id()).update();
        return true;
    }

    private void pollJobs(String type, WorkflowJobHandler handler) {
        var payload = post("/v2/jobs/activation", Map.of("type", type, "worker", "btt",
                "timeout", 30_000, "maxJobsToActivate", 4, "requestTimeout", -1));
        for (JsonNode job : payload.path("jobs")) {
            String key = job.path("jobKey").asText();
            try {
                var variables = handler.handle(type, job.path("variables"));
                post("/v2/jobs/" + key + "/completion", Map.of("variables", variables));
            } catch (RuntimeException ex) {
                LOG.error("Zeebe job {} ({}) failed", key, type, ex);
                post("/v2/jobs/" + key + "/failure", Map.of("retries", Math.max(0, job.path("retries").asInt() - 1),
                        "retryBackOff", 5_000, "errorMessage", "BTT workflow job failed: " + type));
            }
        }
    }

    private JsonNode post(String path, Object body) {
        var response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(String.class);
        return response == null || response.isBlank() ? json.createObjectNode() : json.readTree(response);
    }

    private record Command(UUID id, UUID request, String process, String message, JsonNode variables) {
    }
}
