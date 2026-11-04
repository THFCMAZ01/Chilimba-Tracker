package zm.zut;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Map;

public class Main {

    private static final MemberRepository memberRepository =
        new MemberRepository();

    private static final ContributionRepository contributionRepository =
        new ContributionRepository();

    private static final PayoutRepository payoutRepository =
        new PayoutRepository();

    public static class NewUserRequest {
        public String name;
        public String phone;
        public String role;
    }

    public static void main(String[] args) throws SQLException {

        // Confirm database connectivity before starting the API.
        if (!Database.testConnection()) {
            throw new IllegalStateException(
                "Cannot start application: database connection failed."
            );
        }

        System.out.println("Connected to Neon PostgreSQL.");

        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public");

            config.jsonMapper(
                new JavalinJackson().updateMapper(mapper -> {
                    mapper.registerModule(new JavaTimeModule());
                    mapper.disable(
                        SerializationFeature.WRITE_DATES_AS_TIMESTAMPS
                    );
                })
            );
        });

        // Exception handlers.
        app.exception(MissedContributionException.class, (e, ctx) ->
            ctx.status(400).json(Map.of("error", e.getMessage()))
        );

        app.exception(IllegalArgumentException.class, (e, ctx) ->
            ctx.status(400).json(Map.of("error", e.getMessage()))
        );

        app.exception(IllegalStateException.class, (e, ctx) ->
            ctx.status(409).json(Map.of("error", e.getMessage()))
        );

        app.exception(SQLException.class, (e, ctx) -> {
            System.err.println(
                "Database operation failed: " + e.getMessage()
            );

            ctx.status(500).json(
                Map.of("error", "A database operation failed.")
            );
        });

        // API status.
        app.get("/api/status", ctx ->
            ctx.json(Map.of(
                "status", "running",
                "database", "connected"
            ))
        );

        // Retrieve members from Neon.
        app.get("/members", ctx ->
            ctx.json(memberRepository.findAll())
        );

        // Register a member in Neon.
        app.post("/members", ctx -> {

            NewUserRequest req =
                ctx.bodyAsClass(NewUserRequest.class);

            if (req.name == null || req.name.isBlank()
                    || req.phone == null || req.phone.isBlank()
                    || req.role == null || req.role.isBlank()) {

                throw new IllegalArgumentException(
                    "Name, phone and role are required."
                );
            }

            User newUser = memberRepository.registerMember(
                req.name,
                req.phone,
                req.role
            );

            ctx.status(201).json(newUser);
        });

        // Retrieve current-cycle contributions from Neon.
        app.get("/contributions", ctx ->
            ctx.json(contributionRepository.findAllForCurrentCycle())
        );

        // Record a contribution in Neon.
        app.post("/contributions", ctx -> {

            Contribution payload =
                ctx.bodyAsClass(Contribution.class);

            LocalDate date = payload.getDate() != null
                ? payload.getDate()
                : LocalDate.now();

            Contribution savedContribution =
                contributionRepository.recordContribution(
                    payload.getMemberId(),
                    payload.getAmount(),
                    date
                );

            ctx.status(201).json(savedContribution);
        });

        // Retrieve payout history from Neon.
        app.get("/payouts", ctx ->
            ctx.json(payoutRepository.findAll())
        );

        // Execute and persist a payout in Neon.
        app.post("/payouts", ctx -> {

            Payout payout =
                payoutRepository.executePayout(LocalDate.now());

            ctx.status(201).json(payout);
        });

        int port = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 7070;

        app.start(port);
    }
}