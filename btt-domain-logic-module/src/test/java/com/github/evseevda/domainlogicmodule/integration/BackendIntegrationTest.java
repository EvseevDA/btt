package com.github.evseevda.domainlogicmodule.integration;

import com.github.evseevda.domainlogicmodule.organization.service.OrganizationRequests;
import com.github.evseevda.domainlogicmodule.registration.service.RegistrationRequests;
import com.github.evseevda.domainlogicmodule.workflow.domain.WorkflowCommand;
import com.github.evseevda.domainlogicmodule.workflow.repository.WorkflowDispatchRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
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
        properties = {"spring.cloud.discovery.enabled=false", "camunda.client.enabled=false"})
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
    @Autowired
    private WorkflowDispatchRepository dispatch;
    @Autowired
    private EntityManager entityManager;

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @DynamicPropertySource
    static void dataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> System.getenv("BTT_TEST_DB_URL"));
        registry.add("spring.datasource.username", () -> System.getenv().getOrDefault("BTT_TEST_DB_USER", "btt"));
        registry.add("spring.datasource.password", () -> System.getenv().getOrDefault("BTT_TEST_DB_PASSWORD", "btt_local"));
    }

    @Test
    @Transactional
    void delayedWorkflowCommandBlocksOnlyLaterCommandsForTheSameRequest() {
        var request = UUID.randomUUID();
        var first = UUID.randomUUID();
        var second = UUID.randomUUID();
        var independent = UUID.randomUUID();
        var created = LocalDateTime.of(2000, 1, 1, 0, 0);
        entityManager.persist(new WorkflowCommand(first, request, "blm_test", null, "{}", created));
        entityManager.persist(new WorkflowCommand(second, request, null, "next", "{}", created.plusSeconds(1)));
        entityManager.persist(new WorkflowCommand(independent, UUID.randomUUID(), "blm_test", null, "{}", created.plusSeconds(2)));

        assertEquals(first, dispatch.lockNextCommand().orElseThrow().id());
        dispatch.delayRetry(60, first);
        assertEquals(independent, dispatch.lockNextCommand().orElseThrow().id());
        dispatch.markDelivered(independent);
        assertTrue(dispatch.lockNextCommand().stream().noneMatch(command -> second.equals(command.id())));
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

    @Test
    void duplicateOrganizationRequestIsBlockedAndRejectionAllowsResubmission() throws Exception {
        var applicant = account(false);
        var reviewer = account(true);
        var form = organizationForm();
        var first = expect(201, post("/organization-registrations", applicant.token(), form));
        UUID request = id(first, "id");
        String path = "/organization-requests/" + request;
        expect(409, post("/organization-registrations", applicant.token(), form));
        expect(400, post(path + "/decision", reviewer.token(), Map.of("approved", false)));
        expect(202, post(path + "/decision", reviewer.token(),
                Map.of("approved", false, "comment", "No authorization")));
        organizations.handle("blm_organization_registration_reject", command(request));

        var rejected = expect(200, get(path, applicant.token()));
        assertEquals(3, rejected.path("status").asInt());
        assertEquals("No authorization", rejected.path("decisionComment").asText());
        assertFalse(rejected.hasNonNull("organizationId"));
        expect(201, post("/organization-registrations", applicant.token(), form));
    }

    @Test
    void anotherOrganizationCannotReviewMembershipAndARejectedRequestGrantsNoAccess() throws Exception {
        var administrator = account(true);
        var owner = account(false);
        var otherOwner = account(false);
        var applicant = account(false);
        UUID organization = connect(owner, administrator);
        connect(otherOwner, administrator);
        UUID membership = id(expect(201, post("/organization-memberships", applicant.token(),
                Map.of("organizationId", organization))), "id");
        String path = "/organization-requests/" + membership;
        expect(403, post(path + "/decision", otherOwner.token(), Map.of("approved", true)));
        expect(403, post(path + "/decision", administrator.token(), Map.of("approved", true)));
        expect(202, post(path + "/decision", owner.token(), Map.of("approved", false, "comment", "Unknown employee")));
        organizations.handle("blm_organization_membership_reject", command(membership));

        assertEquals(3, expect(200, get(path, applicant.token())).path("status").asInt());
        var draft = Map.of("type", 1, "organizationId", organization,
                "transporterData", Map.of("transportKindId", kind(), "licenses", List.of()));
        expect(403, post("/registration-requests", applicant.token(), draft));
    }

    @Test
    void approvedMembershipNeedsExplicitDelegationToRepresentOrganization() throws Exception {
        var administrator = account(true);
        var owner = account(false);
        var member = account(false);
        UUID organization = connect(owner, administrator);
        UUID membership = id(expect(201, post("/organization-memberships", member.token(),
                Map.of("organizationId", organization))), "id");
        expect(202, post("/organization-requests/" + membership + "/decision", owner.token(), Map.of("approved", true)));
        organizations.handle("blm_organization_membership_approve", command(membership));
        var draft = Map.of("type", 1, "organizationId", organization,
                "transporterData", Map.of("transportKindId", kind(), "licenses", List.of()));
        expect(403, post("/registration-requests", member.token(), draft));
        String permissions = "/transporter-organizations/" + organization + "/members/" + member.id() + "/permissions";
        var delegation = Map.of("canRepresent", true, "canConfirmMembers", false);
        expect(403, send("PUT", permissions, administrator.token(), delegation));
        expect(204, send("PUT", permissions, owner.token(), delegation));
        expect(201, post("/registration-requests", member.token(), draft));
    }

    @Test
    void staffCanBeRegisteredOnlyByAnAuthorizedAccount() throws Exception {
        var admin = account(true);
        var candidate = account(false);
        String name = "employee-" + UUID.randomUUID();
        var form = Map.of("username", name, "email", name + "@example.test", "fullName", "New employee",
                "password", PASSWORD, "roleNames", List.of("REGISTRATION_EXECUTOR"));
        expect(401, post("/users", null, form));
        expect(403, post("/users", candidate.token(), form));
        UUID employee = id(expect(201, post("/users", admin.token(), form)), "id");
        String roles = "/users/" + employee + "/roles";
        expect(403, get(roles, candidate.token()));
        assertEquals("REGISTRATION_EXECUTOR", expect(200, get(roles, admin.token())).get(0).path("name").asText());
    }

    @Test
    void approvedRefusalDoesNotCreateTransporterOrCertificate() throws Exception {
        var admin = account(true);
        var owner = account(false);
        UUID organization = connect(owner, admin);
        UUID request = submit(1, owner, organization, null, kind());
        finalizeDecision(request, admin, 2, "blm_finalize_registration_refusal");

        var result = expect(200, get(carrierPath(request), owner.token()));
        assertEquals(4, result.path("status").asInt());
        assertFalse(result.hasNonNull("transporterId"));
        assertTrue(expect(200, get("/transporters?organizationId=" + organization, owner.token())).isEmpty());
    }

    @Test
    void amendmentCreatesNewLicenseRecordsAndKeepsOldSnapshotReadable() throws Exception {
        var admin = account(true);
        var owner = account(false);
        UUID organization = connect(owner, admin);
        UUID firstKind = kind();
        UUID secondKind = kind();
        UUID transporter = registerCarrier(owner, admin, organization, firstKind);

        UUID amendment = submit(2, owner, organization, transporter, secondKind);
        finalizeDecision(amendment, admin, 1, "blm_apply_registration_amendment");
        var versions = history(transporter, owner);
        assertEquals(2, versions.size());
        assertEquals(1, versions.get(0).path("status").asInt());
        assertEquals(2, versions.get(1).path("status").asInt());
        var current = snapshot(transporter, versions.get(0), owner);
        var prior = snapshot(transporter, versions.get(1), owner);
        assertEquals(secondKind, id(current, "transportKindId"));
        assertEquals(firstKind, id(prior, "transportKindId"));
        assertEquals(current.get("licenses").get(0).path("licenseNumber"), prior.get("licenses").get(0).path("licenseNumber"));
        assertNotEquals(id(current.get("licenses").get(0), "id"), id(prior.get("licenses").get(0), "id"));
    }

    @ParameterizedTest
    @ValueSource(ints = {3, 4})
    void bothCancellationProcessesRevokeTheOldCertificateVersionAndKeepHistory(int type) throws Exception {
        var admin = account(true);
        var owner = account(false);
        UUID organization = connect(owner, admin);
        UUID transportKind = kind();
        UUID transporter = registerCarrier(owner, admin, organization, transportKind);
        Account submitter = type == 3 ? owner : admin;

        UUID cancellation = submit(type, submitter, organization, transporter, null);
        finalizeDecision(cancellation, admin, 1,
                type == 3 ? "blm_apply_carrier_cancellation" : "blm_apply_admin_cancellation");
        assertEquals(2, expect(200, get("/transporters/" + transporter, owner.token())).path("status").asInt());
        var certificate = expect(200, get("/transporters/" + transporter + "/registration-certificate", owner.token()));
        assertEquals(2, certificate.path("status").asInt());
        var versions = history(transporter, owner);
        assertEquals(2, versions.size());
        assertEquals(3, versions.get(1).path("status").asInt());
        assertEquals(id(versions.get(0), "id"), id(certificate, "currentVersionId"));
        assertNotEquals(id(snapshot(transporter, versions.get(0), owner).get("licenses").get(0), "id"),
                id(snapshot(transporter, versions.get(1), owner).get("licenses").get(0), "id"));
        if (type == 4) expect(404, get(carrierPath(cancellation), owner.token()));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 6, 7})
    void onlyApplicantCanWithdrawAtEveryWaitingStage(int status) throws Exception {
        var admin = account(true);
        var owner = account(false);
        UUID organization = connect(owner, admin);
        UUID request = submit(1, owner, organization, null, kind());
        String path = carrierPath(request);
        if (status != 1) claim(request, admin);
        if (status == 6 || status == 7) {
            int revision = decision(request, admin, 1);
            if (status == 7) {
                expect(202, post(path + "/return", admin.token(), Map.of("revision", revision, "reason", "Revise")));
                registrations.handle("blm_return_registration_decision", command(request));
            }
        }
        assertEquals(status, expect(200, get(path, owner.token())).path("status").asInt());
        expect(403, post(path + "/withdraw", admin.token(), null));
        expect(202, post(path + "/withdraw", owner.token(), null));
        registrations.handle("blm_withdraw_registration_request", command(request));
        var withdrawn = expect(200, get(path, owner.token()));
        assertEquals(5, withdrawn.path("status").asInt());
        assertFalse(withdrawn.hasNonNull("transporterId"));
        expect(409, post(path + "/withdraw", owner.token(), null));
    }

    @Test
    void tripSearchFiltersByRouteEndpointsPriceAndAvailability() throws Exception {
        var passenger = account(false);
        UUID trip = scheduledTrip();
        var details = expect(200, get("/trips/" + trip, passenger.token()));
        UUID route = id(details, "routeId");
        String search = "/trips?routeId=" + route + "&source=Source&destination=Destination&bookableOnly=true";
        assertEquals(trip, id(expect(200, get(search + "&maxPrice=100", passenger.token())).get(0), "id"));
        assertTrue(expect(200, get(search + "&maxPrice=90", passenger.token())).isEmpty());
        expect(201, post("/tickets", passenger.token(), Map.of("tripId", trip)));
        assertTrue(expect(200, get(search, passenger.token())).isEmpty());
        assertEquals(1, expect(200, get("/tickets?tripId=" + trip, passenger.token())).size());
    }

    @Test
    void departedTripsRejectBookingAndCancellation() throws Exception {
        var passenger = account(false);
        UUID trip = scheduledTrip();
        UUID ticket = id(expect(201, post("/tickets", passenger.token(), Map.of("tripId", trip))), "id");
        db.sql("""
                UPDATE trip SET departure_at = timezone('UTC', now()) - interval '1 hour',
                    arrival_at = timezone('UTC', now()) + interval '1 hour' WHERE id = :trip
                """).param("trip", trip).update();
        expect(409, post("/tickets", passenger.token(), Map.of("tripId", trip)));
        expect(409, post("/tickets/" + ticket + "/cancel", passenger.token(), null));
        assertEquals(1, expect(200, get("/tickets/" + ticket, passenger.token())).path("status").asInt());
    }

    private UUID connect(Account owner, Account reviewer) throws Exception {
        UUID request = id(expect(201, post("/organization-registrations", owner.token(), organizationForm())), "id");
        String path = "/organization-requests/" + request;
        expect(202, post(path + "/decision", reviewer.token(), Map.of("approved", true)));
        organizations.handle("blm_organization_registration_approve", command(request));
        return id(expect(200, get(path, owner.token())), "organizationId");
    }

    private Map<String, String> organizationForm() {
        String suffix = String.format(java.util.Locale.ROOT, "%08d",
                java.util.concurrent.ThreadLocalRandom.current().nextInt(100_000_000));
        return Map.of("name", "Integration Organization " + suffix, "inn", "52" + suffix, "ogrn", "10252" + suffix,
                "legalAddress", "Test address", "authorityEvidence", "Test authorization");
    }

    private UUID submit(int type, Account submitter, UUID organization, UUID transporter, UUID transportKind) throws Exception {
        var form = new java.util.HashMap<String, Object>(Map.of("type", type, "organizationId", organization));
        if (transporter != null) form.put("transporterId", transporter);
        if (transportKind != null) {
            var license = Map.of("licenseNumber", "LIC-1", "issuedAt", "2026-01-01", "expiresAt", "2040-01-01",
                    "issuingAuthority", "Test authority");
            form.put("transporterData", Map.of("transportKindId", transportKind, "licenses", List.of(license)));
        }
        UUID request = id(expect(201, post("/registration-requests", submitter.token(), form)), "id");
        expect(200, post(carrierPath(request) + "/submit", submitter.token(), null));
        return request;
    }

    private UUID registerCarrier(Account owner, Account admin, UUID organization, UUID transportKind) throws Exception {
        UUID request = submit(1, owner, organization, null, transportKind);
        finalizeDecision(request, admin, 1, "blm_apply_initial_registration");
        return id(expect(200, get(carrierPath(request), owner.token())), "transporterId");
    }

    private void claim(UUID request, Account admin) throws Exception {
        expect(202, post(carrierPath(request) + "/claim", admin.token(), null));
        registrations.handle("blm_save_registration_assignment", command(request));
    }

    private int decision(UUID request, Account admin, int type) throws Exception {
        var response = expect(201, post(carrierPath(request) + "/decision", admin.token(), Map.of("type", type)));
        registrations.handle("blm_save_registration_decision", command(request));
        return response.path("revision").asInt();
    }

    private void finalizeDecision(UUID request, Account admin, int type, String job) throws Exception {
        claim(request, admin);
        int revision = decision(request, admin, type);
        expect(202, post(carrierPath(request) + "/approve", admin.token(), Map.of("revision", revision)));
        registrations.handle(job, command(request));
    }

    private JsonNode history(UUID transporter, Account owner) throws Exception {
        var certificate = expect(200, get("/transporters/" + transporter + "/registration-certificate", owner.token()));
        return expect(200, get("/registration-certificates/" + id(certificate, "id") + "/versions", owner.token()));
    }

    private JsonNode snapshot(UUID transporter, JsonNode version, Account owner) throws Exception {
        return expect(200, get("/transporters/" + transporter + "/versions/" + id(version, "transporterVersionId"), owner.token()));
    }

    private static String carrierPath(UUID request) {
        return "/registration-requests/" + request;
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
