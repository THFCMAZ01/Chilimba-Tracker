package zm.zut;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MemberRepository {

    private static final UUID GROUP_ID = UUID.fromString(
        "2e2067d4-6ca4-4cef-ba6f-98f335d52df7"
    );

    public User registerMember(
            String name,
            String phone,
            String role) throws SQLException {

        if (name == null || name.isBlank()
                || phone == null || phone.isBlank()
                || role == null || role.isBlank()) {
            throw new IllegalArgumentException(
                "Name, phone and role are required."
            );
        }

        String normalizedRole;

        if ("Treasurer".equalsIgnoreCase(role)) {
            normalizedRole = "TREASURER";
        } else if ("OrdinaryMember".equalsIgnoreCase(role)
                || "Ordinary Member".equalsIgnoreCase(role)) {
            normalizedRole = "ORDINARY_MEMBER";
        } else {
            throw new IllegalArgumentException(
                "Role must be Treasurer or Ordinary Member."
            );
        }

        try (Connection connection = Database.getConnection()) {
            connection.setAutoCommit(false);

            try {
                // Lock the group row to serialize member registrations.
                String lockSql = """
                    SELECT id
                    FROM savings_groups
                    WHERE id = ?
                    FOR UPDATE
                    """;

                try (PreparedStatement lock =
                        connection.prepareStatement(lockSql)) {

                    lock.setObject(1, GROUP_ID);

                    try (ResultSet result = lock.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException(
                                "Chilimba Group was not found."
                            );
                        }
                    }
                }

                // Generate the next registration number.
                String orderSql = """
                    SELECT COALESCE(MAX(registration_order), 0) + 1
                           AS next_order
                    FROM group_members
                    WHERE group_id = ?
                    """;

                int nextOrder;

                try (PreparedStatement statement =
                        connection.prepareStatement(orderSql)) {

                    statement.setObject(1, GROUP_ID);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                "Could not generate registration order."
                            );
                        }

                        nextOrder = result.getInt("next_order");
                    }
                }

                // Generate the readable member code.
                String memberCode = String.format(
                    "MEM-%03d", nextOrder
                );

                // Insert member and retrieve database-generated values.
                String insertSql = """
                    INSERT INTO group_members (
                        group_id,
                        member_code,
                        name,
                        phone,
                        role,
                        registration_order
                    )
                    VALUES (?, ?, ?, ?, ?, ?)
                    RETURNING id, member_code, name, phone, role, join_date
                    """;

                User newMember;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertSql)) {

                    statement.setObject(1, GROUP_ID);
                    statement.setString(2, memberCode);
                    statement.setString(3, name.trim());
                    statement.setString(4, phone.trim());
                    statement.setString(5, normalizedRole);
                    statement.setInt(6, nextOrder);

                    try (ResultSet result = statement.executeQuery()) {

                        if (!result.next()) {
                            throw new SQLException(
                                "Member registration returned no record."
                            );
                        }

                        String id = result.getObject("id").toString();
                        String savedMemberCode =
                            result.getString("member_code");
                        String savedName = result.getString("name");
                        String savedPhone = result.getString("phone");
                        String savedRole = result.getString("role");

                        LocalDate joinDate =
                            result.getDate("join_date").toLocalDate();

                        if ("TREASURER".equals(savedRole)) {
                            newMember = new Treasurer(
                                id, savedName, savedPhone, joinDate
                            );
                        } else {
                            newMember = new OrdinaryMember(
                                id, savedName, savedPhone, joinDate
                            );
                        }

                        newMember.setMemberCode(savedMemberCode);
                    }
                }

                connection.commit();
                return newMember;

            } catch (Exception e) {
                connection.rollback();

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                throw new SQLException(
                    "Member registration failed.", e
                );
            }
        }
    }

    public List<User> findAll() throws SQLException {

        List<User> members = new ArrayList<>();

        String sql = """
            SELECT
                id,
                member_code,
                name,
                phone,
                role,
                join_date
            FROM group_members
            WHERE group_id = ?
              AND status = 'ACTIVE'
            ORDER BY registration_order ASC
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {

                    String id = result.getObject("id").toString();
                    String memberCode =
                        result.getString("member_code");
                    String name = result.getString("name");
                    String phone = result.getString("phone");
                    String role = result.getString("role");

                    LocalDate joinDate =
                        result.getDate("join_date").toLocalDate();

                    User member;

                    if ("TREASURER".equals(role)) {
                        member = new Treasurer(
                            id, name, phone, joinDate
                        );
                    } else {
                        member = new OrdinaryMember(
                            id, name, phone, joinDate
                        );
                    }

                    member.setMemberCode(memberCode);
                    members.add(member);
                }
            }
        }

        return List.copyOf(members);
    }
}