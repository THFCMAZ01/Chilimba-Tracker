package zm.zut;

import io.javalin.Javalin;
import io.javalin.json.JavalinJackson;
import org.eclipse.jetty.http.HttpCookie;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Map;

public class Main {

    private static final MemberRepository memberRepository =
        new MemberRepository();

    private static final ContributionRepository contributionRepository =
        new ContributionRepository();

    private static final PayoutRepository payoutRepository =
        new PayoutRepository();

    private static final AuthService authService =
        new AuthService();

    public static class NewUserRequest {
        public String name;
        public String phone;
        public String role;
    }

    public static class LoginRequest {
        public String email;
        public String password;
    }

    public static void main(String[] args) throws SQLException {

        if (!Database.testConnection()) {
            throw new IllegalStateException(
                "Cannot start application: database connection failed."
            );
        }

        System.out.println("Connected to Neon PostgreSQL.");

        Javalin app = Javalin.create(config -> {
            config.jetty.modifyServletContextHandler(handler -> {
                var sessionHandler = handler.getSessionHandler();

                // Protect session cookies.
                sessionHandler.setHttpOnly(true);
                sessionHandler.setSameSite(HttpCookie.SameSite.LAX);

                // Set Secure only when the request uses HTTPS.
                sessionHandler.setSecureRequestOnly(true);
            });

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

        // Public health-check endpoint.
        app.get("/api/status", ctx ->
            ctx.json(Map.of(
                "status", "running",
                "database", "connected"
            ))
        );

        // Authenticate an existing account.
        app.post("/api/auth/login", ctx -> {
            LoginRequest req =
                ctx.bodyAsClass(LoginRequest.class);

            if (req.email == null || req.password == null) {
                ctx.status(400).json(
                    Map.of("error", "Email and password are required.")
                );
                return;
            }

            char[] password = req.password.toCharArray();

            try {
                var authenticatedUser =
                    authService.authenticate(req.email, password);

                if (authenticatedUser.isEmpty()) {
                    ctx.status(401).json(
                        Map.of("error", "Invalid email or password.")
                    );
                    return;
                }

                SessionSecurity.login(
                    ctx,
                    authenticatedUser.get()
                );

                ctx.json(Map.of(
                    "message", "Login successful."
                ));
            } finally {
                Arrays.fill(password, '\0');
                req.password = null;
            }
        });

        // Return the current account and its CSRF token.
        app.get("/api/auth/me", ctx -> {
            if (!SessionSecurity.requireLogin(ctx)) {
                return;
            }

            AuthenticatedUser user =
                SessionSecurity.currentUser(ctx);

            ctx.json(Map.of(
                "userId", user.getUserId(),
                "memberId", user.getMemberId(),
                "email", user.getEmail(),
                "memberCode", user.getMemberCode(),
                "name", user.getName(),
                "role", user.getRole(),
                "csrfToken", SessionSecurity.csrfToken(ctx)
            ));
        });

        // Log out and invalidate the current session.
        app.post("/api/auth/logout", ctx -> {
            if (!SessionSecurity.requireLogin(ctx)) {
                return;
            }

            if (!SessionSecurity.requireCsrf(ctx)) {
                return;
            }

            SessionSecurity.logout(ctx);

            ctx.json(Map.of("message", "Logged out."));
        });

        // Read members: any authenticated member.
        app.get("/members", ctx -> {
            if (!SessionSecurity.requireLogin(ctx)) {
                return;
            }

            ctx.json(memberRepository.findAll());
        });

        // Register members: Treasurer only.
        app.post("/members", ctx -> {
            if (!SessionSecurity.requireTreasurer(ctx)) {
                return;
            }

            if (!SessionSecurity.requireCsrf(ctx)) {
                return;
            }

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

        // Read contributions: any authenticated member.
        app.get("/contributions", ctx -> {
            if (!SessionSecurity.requireLogin(ctx)) {
                return;
            }

            ctx.json(
                contributionRepository.findAllForCurrentCycle()
            );
        });

        // Record contributions: Treasurer only.
        app.post("/contributions", ctx -> {
            if (!SessionSecurity.requireTreasurer(ctx)) {
                return;
            }

            if (!SessionSecurity.requireCsrf(ctx)) {
                return;
            }

            AuthenticatedUser user =
                SessionSecurity.currentUser(ctx);

            Contribution payload =
                ctx.bodyAsClass(Contribution.class);

            LocalDate date = payload.getDate() != null
                ? payload.getDate()
                : LocalDate.now();

            Contribution savedContribution =
                contributionRepository.recordContribution(
                    payload.getMemberId(),
                    payload.getAmount(),
                    date,
                    user.getUserId()
                );

            ctx.status(201).json(savedContribution);
        });

        // Read payout history: any authenticated member.
        app.get("/payouts", ctx -> {
            if (!SessionSecurity.requireLogin(ctx)) {
                return;
            }

            ctx.json(payoutRepository.findAll());
        });

        // Execute payouts: Treasurer only.
        app.post("/payouts", ctx -> {
            if (!SessionSecurity.requireTreasurer(ctx)) {
                return;
            }

            if (!SessionSecurity.requireCsrf(ctx)) {
                return;
            }

            AuthenticatedUser user =
                SessionSecurity.currentUser(ctx);

            Payout payout =
                payoutRepository.executePayout(
                    LocalDate.now(),
                    user.getUserId()
                );

            ctx.status(201).json(payout);
        });

        int port = System.getenv("PORT") != null
            ? Integer.parseInt(System.getenv("PORT"))
            : 7070;

        app.start(port);
    }
}
