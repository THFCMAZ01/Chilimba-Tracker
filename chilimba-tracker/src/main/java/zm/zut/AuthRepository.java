package zm.zut;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class AuthRepository {

    private static final UUID GROUP_ID = UUID.fromString(
        "2e2067d4-6ca4-4cef-ba6f-98f335d52df7"
    );

    public record LoginRecord(
        AuthenticatedUser user,
        String passwordHash
    ) {}

    public Optional<LoginRecord> findByEmail(String email)
            throws SQLException {

        String sql = """
            SELECT
                u.id AS user_id,
                u.email,
                u.password_hash,
                gm.id AS member_id,
                gm.member_code,
                gm.name,
                gm.role
            FROM users u
            JOIN group_members gm
                ON gm.user_id = u.id
            WHERE LOWER(u.email) = ?
              AND gm.group_id = ?
              AND gm.status = 'ACTIVE'
            LIMIT 1
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setString(1, email);
            statement.setObject(2, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    return Optional.empty();
                }

                UUID userId = result.getObject(
                    "user_id", UUID.class
                );

                UUID memberId = result.getObject(
                    "member_id", UUID.class
                );

                AuthenticatedUser user = new AuthenticatedUser(
                    userId,
                    memberId,
                    result.getString("email"),
                    result.getString("member_code"),
                    result.getString("name"),
                    result.getString("role")
                );

                return Optional.of(new LoginRecord(
                    user,
                    result.getString("password_hash")
                ));
            }
        }
    }

    public Optional<AuthenticatedUser> findActiveUser(UUID userId)
            throws SQLException {

        String sql = """
            SELECT
                u.id AS user_id,
                u.email,
                gm.id AS member_id,
                gm.member_code,
                gm.name,
                gm.role
            FROM users u
            JOIN group_members gm
                ON gm.user_id = u.id
            WHERE u.id = ?
              AND gm.group_id = ?
              AND gm.status = 'ACTIVE'
            LIMIT 1
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setObject(1, userId);
            statement.setObject(2, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    return Optional.empty();
                }

                AuthenticatedUser user = new AuthenticatedUser(
                    result.getObject("user_id", UUID.class),
                    result.getObject("member_id", UUID.class),
                    result.getString("email"),
                    result.getString("member_code"),
                    result.getString("name"),
                    result.getString("role")
                );

                return Optional.of(user);
            }
        }
    }

}
