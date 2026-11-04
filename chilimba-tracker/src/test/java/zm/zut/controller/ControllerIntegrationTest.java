package zm.zut.controller;

import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import zm.zut.*;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class ControllerIntegrationTest {

    private Javalin app;
    private String baseUrl;
    private final HttpClient client = HttpClient.newHttpClient();

    private final FakeMemberRepository memberRepository =
        new FakeMemberRepository();

    private final FakeContributionRepository contributionRepository =
        new FakeContributionRepository();

    private final FakePayoutRepository payoutRepository =
        new FakePayoutRepository();

    @BeforeEach
    void startServer() {

        app = Javalin.create(config ->
            config.jsonMapper(
                new JavalinJackson().updateMapper(mapper -> {
                    mapper.registerModule(new JavaTimeModule());
                    mapper.disable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                    );
                })
            )
        );

        app.exception(IllegalArgumentException.class, (e, ctx) ->
            ctx.status(400).json(Map.of("error", e.getMessage()))
        );

        new MemberController(memberRepository).registerRoutes(app);

        new ContributionController(contributionRepository)
            .registerRoutes(app);

        new PayoutController(payoutRepository).registerRoutes(app);

        app.start(0);

        baseUrl = "http://localhost:" + app.port();
    }

    @AfterEach
    void stopServer() {
        if (app != null) {
            app.stop();
        }
    }

    private HttpResponse<String> get(String path) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .GET()
            .build();

        return client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
    }

    private HttpResponse<String> post(
            String path,
            String json) throws Exception {

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + path))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();

        return client.send(
            request,
            HttpResponse.BodyHandlers.ofString()
        );
    }

    @Test
    void getsMembers() throws Exception {

        HttpResponse<String> response = get("/members");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Joshua"));
    }

    @Test
    void registersMember() throws Exception {

        HttpResponse<String> response = post(
            "/members",
            """
            {
                "name": "Test Member",
                "phone": "0971234567",
                "role": "Treasurer"
            }
            """
        );

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("Test Member"));
        assertTrue(response.body().contains("Treasurer"));
    }

    @Test
    void rejectsMemberRegistrationWithMissingFields()
            throws Exception {

        HttpResponse<String> response = post(
            "/members",
            "{}"
        );

        assertEquals(400, response.statusCode());
        assertTrue(response.body().contains("required"));
    }

    @Test
    void getsContributions() throws Exception {

        HttpResponse<String> response = get("/contributions");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("1000"));
    }

    @Test
    void recordsContributionAndDefaultsMissingDate()
            throws Exception {

        HttpResponse<String> response = post(
            "/contributions",
            """
            {
                "memberId": "2410123",
                "amount": 1000
            }
            """
        );

        assertEquals(201, response.statusCode());
        assertNotNull(contributionRepository.recordedDate);
        assertEquals(
            LocalDate.now(),
            contributionRepository.recordedDate
        );
    }

    @Test
    void getsPayoutHistory() throws Exception {

        HttpResponse<String> response = get("/payouts");

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("2500"));
    }

    @Test
    void executesPayoutThroughTestRepository()
            throws Exception {

        HttpResponse<String> response = post("/payouts", "");

        assertEquals(201, response.statusCode());
        assertTrue(response.body().contains("2500"));
    }

    // Test doubles: these never connect to the real database.

    private static class FakeMemberRepository
            extends MemberRepository {

        @Override
        public List<User> findAll() {

            return List.of(
                new Treasurer(
                    "2410123",
                    "Joshua",
                    "0979615785",
                    LocalDate.now()
                )
            );
        }

        @Override
        public User registerMember(
                String name,
                String phone,
                String role) {

            return new Treasurer(
                "test-id",
                name,
                phone,
                LocalDate.now()
            );
        }
    }

    private static class FakeContributionRepository
            extends ContributionRepository {

        private LocalDate recordedDate;

        @Override
        public List<Contribution> findAllForCurrentCycle() {

            return List.of(
                new Contribution(
                    "2410123",
                    new BigDecimal("1000.00"),
                    LocalDate.now()
                )
            );
        }

        @Override
        public Contribution recordContribution(
                String memberId,
                BigDecimal amount,
                LocalDate date) {

            recordedDate = date;

            return new Contribution(
                memberId,
                amount,
                date
            );
        }
    }

    private static class FakePayoutRepository
            extends PayoutRepository {

        @Override
        public List<Payout> findAll() {

            return List.of(
                new Payout(
                    "2410123",
                    new BigDecimal("2500.00"),
                    LocalDate.now()
                )
            );
        }

        @Override
        public Payout executePayout(LocalDate date) {

            return new Payout(
                "2410123",
                new BigDecimal("2500.00"),
                date
            );
        }
    }
}