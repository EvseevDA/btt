package com.github.evseevda.domainlogicmodule.integration;

import com.github.evseevda.domainlogicmodule.organization.service.OrganizationRequests;
import com.github.evseevda.domainlogicmodule.registration.service.RegistrationRequests;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.cloud.discovery.enabled=false", "btt.zeebe.url=false"})
@EnabledIfEnvironmentVariable(named = "BTT_TEST_DB_URL", matches = "jdbc:postgresql:.*")
class BackendIntegrationTest {
    private static final String PASSWORD = "integration-password-123";

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcClient db;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private OrganizationRequests organizations;
    @Autowired
    private RegistrationRequests registrations;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("BTT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("BTT_TEST_DB_USER", "btt"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("BTT_TEST_DB_PASSWORD", "btt_local"));
    }

    @Test
    void organizationAndCarrierWorkflowsEnforcePermissionsAndApplyARevisedDecisionOnlyOnce() throws Exception {
        assertEquals(List.of("1", "2", "3", "4", "5", "6", "7", "8", "9"), db.sql("""
                SELECT version FROM flyway_schema_history WHERE success AND version IS NOT NULL ORDER BY installed_rank
                """).query(String.class).list());

        var applicant = account(false);
        var admin = account(true);
        var teammate = account(false);
        expect(403, post("/users", applicant.token(), Map.of("username", "forbidden-" + UUID.randomUUID(),
                "email", UUID.randomUUID() + "@example.test", "fullName", "Forbidden", "password", PASSWORD,
                "roleNames", List.of("PORTAL_ADMIN"))));

        String digits = String.format(java.util.Locale.ROOT, "%08d",
                java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000));
        var onboarding = expect(201, post("/organization-registrations", applicant.token(),
                Map.of("name", "Integration Carrier", "inn", "52" + digits, "ogrn", "10252" + digits,
                        "legalAddress", "Test address", "authorityEvidence", "Test authorization")));
        UUID onboardingId = id(onboarding, "id");
        String onboard = "/organization-requests/" + onboardingId;
        assertEquals("blm_organization_registration", db.sql("""
                SELECT process_id FROM workflow_command WHERE request_id = :id AND process_id IS NOT NULL
                """).param("id", onboardingId).query(String.class).single());
        expect(403, post(onboard + "/decision", teammate.token(), Map.of("approved", true)));
        expect(202, post(onboard + "/decision", admin.token(), Map.of("approved", true)));
        organizations.handle("blm_organization_registration_approve", command(onboardingId));
        UUID organization = id(expect(200, get(onboard, applicant.token())), "organizationId");

        var membership = expect(201, post("/organization-memberships", teammate.token(),
                Map.of("organizationId", organization)));
        UUID membershipId = id(membership, "id");
        String memberPath = "/organization-requests/" + membershipId;
        expect(403, post(memberPath + "/decision", admin.token(), Map.of("approved", true)));
        expect(202, post(memberPath + "/decision", applicant.token(), Map.of("approved", true)));
        organizations.handle("blm_organization_membership_approve", command(membershipId));
        assertEquals(2, expect(200, get(memberPath, teammate.token())).path("status").asInt());
        assertFalse(db.sql("""
                SELECT can_represent FROM transporter_organization_users WHERE organization_id = :org AND user_id = :user
                """).param("org", organization).param("user", teammate.id()).query(Boolean.class).single());

        UUID kind = kind();
        var request = expect(201, post("/registration-requests", applicant.token(), Map.of("type", 1,
                "organizationId", organization, "transporterData", Map.of("transportKindId", kind, "licenses", List.of()))));
        UUID requestId = id(request, "id");
        String path = "/registration-requests/" + requestId;
        expect(200, post(path + "/submit", applicant.token(), null));
        expect(202, post(path + "/claim", admin.token(), null));
        registrations.handle("blm_save_registration_assignment", command(requestId));
        expect(403, post(path + "/withdraw", teammate.token(), null));

        var initial = expect(201, post(path + "/decision", admin.token(), Map.of("type", 1)));
        int revision = initial.path("revision").asInt();
        registrations.handle("blm_save_registration_decision", command(requestId));
        expect(202, post(path + "/return", admin.token(), Map.of("revision", revision, "reason", "Review again")));
        registrations.handle("blm_return_registration_decision", command(requestId));

        var revised = expect(201, post(path + "/decision", admin.token(), Map.of("type", 1)));
        registrations.handle("blm_save_registration_decision", command(requestId));
        expect(409, post(path + "/approve", admin.token(), Map.of("revision", revision)));
        expect(202, post(path + "/approve", admin.token(), Map.of("revision", revised.path("revision").asInt())));
        var approval = command(requestId);
        registrations.handle("blm_apply_initial_registration", approval);
        registrations.handle("blm_apply_initial_registration", approval);

        var result = expect(200, get(path, applicant.token()));
        assertEquals(3, result.path("status").asInt());
        UUID transporter = id(result, "transporterId");
        assertEquals(1, db.sql("SELECT count(*) FROM transporter_version WHERE transporter_id = :id")
                .param("id", transporter).query(Integer.class).single());
        var certificate = expect(200, get("/transporters/" + transporter + "/registration-certificate", applicant.token()));
        assertTrue(certificate.hasNonNull("currentVersionId"));
        expect(404, get(path, teammate.token()));
    }

    @Test
    void twoPassengersCannotBookTheSameSeatAndCancellationMakesItAvailable() throws Exception {
        var first = account(false);
        var second = account(false);
        UUID trip = scheduledTrip();
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return post("/tickets", first.token(), Map.of("tripId", trip, "seatCode", "A1"));
            });
            var b = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return post("/tickets", second.token(), Map.of("tripId", trip, "seatCode", "A1"));
            });
            var responses = List.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(List.of(201, 409), responses.stream().map(HttpResponse::statusCode).sorted().toList());
            int winnerIndex = responses.get(0).statusCode() == 201 ? 0 : 1;
            var winner = winnerIndex == 0 ? first : second;
            var loser = winnerIndex == 0 ? second : first;
            var ticket = json.readTree(responses.get(winnerIndex).body());
            assertEquals(new BigDecimal("99.90"), ticket.path("price").decimalValue().setScale(2));
            String ticketPath = "/tickets/" + id(ticket, "id");
            expect(404, get(ticketPath, loser.token()));
            expect(404, post(ticketPath + "/cancel", loser.token(), null));
            assertEquals(2, expect(200, post(ticketPath + "/cancel", winner.token(), null)).path("status").asInt());
            expect(200, post(ticketPath + "/cancel", winner.token(), null));
            var rebooked = expect(201, post("/tickets", loser.token(), Map.of("tripId", trip)));
            assertEquals("A1", rebooked.path("seatCode").asText());
            assertFalse(expect(200, get("/trips/" + trip + "/seats", winner.token())).get(0).path("available").asBoolean());
        } finally {
            executor.shutdownNow();
        }
    }

    private Account account(boolean admin) throws Exception {
        String username = "integration-" + UUID.randomUUID();
        UUID id = id(expect(201, post("/users", null, Map.of("username", username, "email", username + "@example.test",
                "fullName", "Integration User", "password", PASSWORD))), "id");
        if (admin) {
            db.sql("INSERT INTO user_roles (user_id, role_id) SELECT :id, id FROM role WHERE name = 'PORTAL_ADMIN'")
                    .param("id", id).update();
        }
        String token = expect(200, post("/auth/token", null, Map.of("username", username, "password", PASSWORD)))
                .path("token").asText();
        return new Account(id, token);
    }

    private UUID kind() {
        UUID kind = UUID.randomUUID();
        db.sql("INSERT INTO transport_kind (id, name) VALUES (:id, :name)")
                .param("id", kind).param("name", kind.toString()).update();
        return kind;
    }

    private UUID scheduledTrip() {
        UUID kind = kind();
        UUID organization = UUID.randomUUID();
        UUID carrier = UUID.randomUUID();
        UUID route = UUID.randomUUID();
        UUID trip = UUID.randomUUID();
        var now = LocalDateTime.now(Clock.systemUTC());
        String key = organization.toString().replace("-", "");
        db.sql("""
                INSERT INTO transporter_organization (id, name, inn, ogrn, legal_address, created_at)
                VALUES (:id, 'Integration Transport', :inn, :ogrn, 'Test address', :now)
                """).param("id", organization).param("inn", key.substring(0, 12))
                .param("ogrn", key.substring(0, 15)).param("now", now).update();
        db.sql("""
                INSERT INTO transporter (id, organization_id, transport_kind_id, status, created_at)
                VALUES (:id, :org, :kind, 1, :now)
                """).param("id", carrier).param("org", organization).param("kind", kind).param("now", now).update();
        db.sql("INSERT INTO route (id, source, dest, duration, transport_kind_id) VALUES (:id, 'Source', 'Destination', 60, :kind)")
                .param("id", route).param("kind", kind).update();
        db.sql("""
                INSERT INTO trip (id, route_id, transporter_id, departure_at, arrival_at, status, base_price)
                VALUES (:id, :route, :carrier, :departure, :arrival, 1, 99.90)
                """).param("id", trip).param("route", route).param("carrier", carrier)
                .param("departure", now.plusDays(1)).param("arrival", now.plusDays(1).plusHours(1)).update();
        db.sql("INSERT INTO trip_seat (trip_id, seat_code) VALUES (:trip, 'A1')").param("trip", trip).update();
        return trip;
    }

    private JsonNode command(UUID requestId) {
        return db.sql("""
                SELECT variables FROM workflow_command WHERE request_id = :request AND message_name IS NOT NULL
                ORDER BY created_at DESC, id DESC LIMIT 1
                """).param("request", requestId).query((rs, row) -> json.readTree(rs.getString("variables"))).single();
    }

    private HttpResponse<String> get(String path, String token) throws Exception {
        return send("GET", path, token, null);
    }

    private HttpResponse<String> post(String path, String token, Object body) throws Exception {
        return send("POST", path, token, body);
    }

    private HttpResponse<String> send(String method, String path, String token, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/v1" + path))
                .timeout(Duration.ofSeconds(10));
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.header("Content-Type", "application/json");
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body))).build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode expect(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), () -> response.request().uri() + ": " + response.body());
        return json.readTree(response.body());
    }

    private static UUID id(JsonNode response, String field) {
        return UUID.fromString(response.path(field).asText());
    }

    private record Account(UUID id, String token) {
    }
}
