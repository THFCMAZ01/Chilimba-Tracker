package zm.zut;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import zm.zut.controller.MemberController;
import zm.zut.controller.ContributionController;
import zm.zut.controller.PayoutController;

import java.sql.SQLException;
import java.util.Map;

public class Main {

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

        // Create repository dependencies.
        MemberRepository memberRepository = new MemberRepository();
        ContributionRepository contributionRepository =
            new ContributionRepository();
        PayoutRepository payoutRepository = new PayoutRepository();

        // Register API controllers.
        new MemberController(memberRepository).registerRoutes(app);

        new ContributionController(contributionRepository)
            .registerRoutes(app);

        new PayoutController(payoutRepository).registerRoutes(app);

        int port = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 7070;

        app.start(port);
    }
}
