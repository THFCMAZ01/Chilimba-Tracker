package zm.zut;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

public final class BootstrapTreasurer {

    private static final UUID GROUP_ID = UUID.fromString(
        "2e2067d4-6ca4-4cef-ba6f-98f335d52df7"
    );

    private static final String MEMBER_CODE = "MEM-005";

    private BootstrapTreasurer() {
    }

    public static void main(String[] args) {
        java.io.Console console = System.console();

        if (console == null) {
            System.err.println(
                "A real terminal is required. Run this utility directly "
                + "from the terminal, not through an IDE console."
            );
            return;
        }

        char[] password = null;
        char[] confirmation = null;

        try {
            System.out.println("CHILIMBA TRACKER - TREASURER ACCOUNT SETUP");
            System.out.println("Target member: MEM-005 (Martin Muloshi)");
            System.out.println();

            String email = console.readLine(
                "Enter the Treasurer's login email: "
            );

            if (email == null) {
                return;
            }

            email = email.trim().toLowerCase(Locale.ROOT);

            if (email.isBlank()
                    || email.length() > 254
                    || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
                System.err.println("A valid email address is required.");
                return;
            }

            password = console.readPassword(
                "Create a password (minimum 12 characters): "
            );

            confirmation = console.readPassword(
                "Confirm the password: "
            );

            if (password == null || confirmation == null
                    || !Arrays.equals(password, confirmation)) {
                System.err.println("Passwords do not match.");
                return;
            }

            String passwordHash =
                PasswordService.hashPassword(password);

            String approval = console.readLine(
                "To confirm, type LINK MEM-005: "
            );

            if (!"LINK MEM-005".equals(approval)) {
                System.out.println("Cancelled. No database changes made.");
                return;
            }

            createAccount(email, passwordHash);

            System.out.println();
            System.out.println(
                "SUCCESS: Treasurer login account created and linked."
            );
            System.out.println("Member code: " + MEMBER_CODE);
            System.out.println("Login email: " + email);

        } catch (IllegalArgumentException e) {
            System.err.println("Invalid input: " + e.getMessage());

        } catch (SQLException e) {
            System.err.println(
                "Account setup failed. Database transaction rolled back."
            );
            System.err.println("Reason: " + e.getMessage());

        } catch (Exception e) {
            System.err.println(
                "Account setup failed. No success was confirmed."
            );
            System.err.println("Reason: " + e.getMessage());

        } finally {
            if (password != null) {
                Arrays.fill(password, '\0');
            }
            if (confirmation != null) {
                Arrays.fill(confirmation, '\0');
            }
        }
    }

    private static void createAccount(
            String email,
            String passwordHash) throws SQLException {

        try (Connection connection = Database.getConnection()) {

            connection.setAutoCommit(false);
            connection.setTransactionIsolation(
                Connection.TRANSACTION_SERIALIZABLE
            );

            try {
                UUID memberId;

                String memberSql = """
                    SELECT id, name, role, status, user_id
                    FROM group_members
                    WHERE group_id = ?
                      AND member_code = ?
                    FOR UPDATE
                    """;

                try (PreparedStatement statement =
                         connection.prepareStatement(memberSql)) {

                    statement.setObject(1, GROUP_ID);
                    statement.setString(2, MEMBER_CODE);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                "MEM-005 was not found in the target group."
                            );
                        }

                        memberId = result.getObject("id", UUID.class);

                        if (!"Martin Muloshi".equals(
                                result.getString("name"))) {
                            throw new SQLException(
                                "The target member name does not match."
                            );
                        }

                        if (!"TREASURER".equals(
                                result.getString("role"))) {
                            throw new SQLException(
                                "The target member is not the Treasurer."
                            );
                        }

                        if (!"ACTIVE".equals(
                                result.getString("status"))) {
                            throw new SQLException(
                                "The Treasurer is not active."
                            );
                        }

                        if (result.getObject("user_id") != null) {
                            throw new SQLException(
                                "The Treasurer already has a linked account."
                            );
                        }
                    }
                }

                // Serialize account creation attempts for the same email.
                String emailLock = """
                    SELECT pg_advisory_xact_lock(
                        hashtextextended(LOWER(?), 0)
                    )
                    """;

                try (PreparedStatement statement =
                         connection.prepareStatement(emailLock)) {
                    statement.setString(1, email);
                    statement.execute();
                }

                String emailCheck = """
                    SELECT id
                    FROM users
                    WHERE LOWER(email) = LOWER(?)
                    LIMIT 1
                    """;

                try (PreparedStatement statement =
                         connection.prepareStatement(emailCheck)) {

                    statement.setString(1, email);

                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            throw new SQLException(
                                "This email already belongs to an account."
                            );
                        }
                    }
                }

                UUID userId;

                String insertUser = """
                    INSERT INTO users (email, password_hash)
                    VALUES (?, ?)
                    RETURNING id
                    """;

                try (PreparedStatement statement =
                         connection.prepareStatement(insertUser)) {

                    statement.setString(1, email);
                    statement.setString(2, passwordHash);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                "The new user account could not be created."
                            );
                        }

                        userId = result.getObject("id", UUID.class);
                    }
                }

                String linkMember = """
                    UPDATE group_members
                    SET user_id = ?
                    WHERE id = ?
                      AND group_id = ?
                      AND user_id IS NULL
                      AND role = 'TREASURER'
                      AND status = 'ACTIVE'
                    """;

                try (PreparedStatement statement =
                         connection.prepareStatement(linkMember)) {

                    statement.setObject(1, userId);
                    statement.setObject(2, memberId);
                    statement.setObject(3, GROUP_ID);

                    if (statement.executeUpdate() != 1) {
                        throw new SQLException(
                            "The Treasurer account could not be linked."
                        );
                    }
                }

                connection.commit();

            } catch (Exception e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                throw new SQLException(
                    "Account setup failed.", e
                );
            }
        }
    }
}
