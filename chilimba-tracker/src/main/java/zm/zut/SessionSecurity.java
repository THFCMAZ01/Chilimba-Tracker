package zm.zut;

import io.javalin.http.Context;
import jakarta.servlet.http.HttpSession;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

public final class SessionSecurity {

    private static final String USER_KEY =
        "authenticatedUser";

    private static final String CSRF_KEY =
        "csrfToken";

    private static final int SESSION_TIMEOUT_SECONDS =
        30 * 60;

    private static final SecureRandom RANDOM =
        new SecureRandom();

    private SessionSecurity() {
    }

    public static void login(
            Context ctx,
            AuthenticatedUser user) {

        if (user == null) {
            throw new IllegalArgumentException(
                "Authenticated user is required."
            );
        }

        HttpSession oldSession =
            ctx.req().getSession(false);

        if (oldSession != null) {
            oldSession.invalidate();
        }

        HttpSession session =
            ctx.req().getSession(true);

        // Rotate the session ID to prevent session fixation.
        ctx.req().changeSessionId();

        session.setMaxInactiveInterval(
            SESSION_TIMEOUT_SECONDS
        );

        String csrfToken = generateCsrfToken();

        session.setAttribute(USER_KEY, user);
        session.setAttribute(CSRF_KEY, csrfToken);
    }

    public static AuthenticatedUser currentUser(
            Context ctx) {

        HttpSession session =
            ctx.req().getSession(false);

        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(USER_KEY);

        if (!(value instanceof AuthenticatedUser)) {
            return null;
        }

        return (AuthenticatedUser) value;
    }

    public static String csrfToken(Context ctx) {

        HttpSession session =
            ctx.req().getSession(false);

        if (session == null) {
            return null;
        }

        Object value = session.getAttribute(CSRF_KEY);

        return value instanceof String
            ? (String) value
            : null;
    }

    public static boolean requireLogin(Context ctx)
            throws SQLException {

        AuthenticatedUser sessionUser = currentUser(ctx);

        if (sessionUser == null) {
            rejectUnauthenticated(ctx);
            return false;
        }

        HttpSession session = ctx.req().getSession(false);

        Optional<AuthenticatedUser> activeUser =
            new AuthRepository().findActiveUser(
                sessionUser.getUserId()
            );

        if (activeUser.isEmpty()) {
            session.invalidate();
            rejectUnauthenticated(ctx);
            return false;
        }

        // Refresh the session with the current database role and details.
        session.setAttribute(USER_KEY, activeUser.get());

        return true;
    }

    private static void rejectUnauthenticated(Context ctx) {
        ctx.status(401).json(
            Map.of("error", "Authentication required.")
        );

        ctx.skipRemainingHandlers();
    }

    public static boolean requireTreasurer(Context ctx)
            throws SQLException {

        if (!requireLogin(ctx)) {
            return false;
        }

        AuthenticatedUser user = currentUser(ctx);

        if (!user.isTreasurer()) {
            ctx.status(403).json(
                Map.of(
                    "error",
                    "Only the Treasurer can perform this action."
                )
            );

            ctx.skipRemainingHandlers();
            return false;
        }

        return true;
    }

    public static boolean requireCsrf(Context ctx) {

        String expected = csrfToken(ctx);
        String supplied =
            ctx.header("X-CSRF-Token");

        if (expected == null || supplied == null) {
            ctx.status(403).json(
                Map.of("error", "Invalid CSRF token.")
            );

            ctx.skipRemainingHandlers();
            return false;
        }

        boolean valid = MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );

        if (!valid) {
            ctx.status(403).json(
                Map.of("error", "Invalid CSRF token.")
            );

            ctx.skipRemainingHandlers();
            return false;
        }

        return true;
    }

    public static void logout(Context ctx) {

        HttpSession session =
            ctx.req().getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }

    private static String generateCsrfToken() {

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);

        return Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(bytes);
    }
}
