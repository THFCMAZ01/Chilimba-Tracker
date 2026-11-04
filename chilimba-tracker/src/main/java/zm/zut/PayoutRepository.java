package zm.zut;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class PayoutRepository {

    private static final UUID GROUP_ID = UUID.fromString(
        "2e2067d4-6ca4-4cef-ba6f-98f335d52df7"
    );

    private static final String SYSTEM_USER_EMAIL =
        "system@chilimba.local";

    private final PayoutRule payoutRule;

    public PayoutRepository() {
        this.payoutRule = new RotationPayout();
    }

    /**
     * Executes a payout for the current open cycle.
     *
     * The payout, cycle closure and next cycle creation
     * happen in one database transaction.
     */
    public Payout executePayout(LocalDate payoutDate)
            throws SQLException {

        LocalDate date = payoutDate == null
            ? LocalDate.now()
            : payoutDate;

        try (Connection connection = Database.getConnection()) {

            connection.setAutoCommit(false);

            try {
                // Serialize financial operations for this group.
                lockGroup(connection);

                UUID cycleId = findOpenCycle(connection);

                List<User> members = findActiveMembers(connection);

                if (members.isEmpty()) {
                    throw new IllegalStateException(
                        "There are no active members in this group."
                    );
                }

                // Every active member must contribute at least once.
                validateMemberContributions(connection, cycleId, members);

                // Calculate the actual contribution pool.
                BigDecimal totalAmount =
                    calculateCycleTotal(connection, cycleId);

                if (totalAmount.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalStateException(
                        "There are no contributions available for payout."
                    );
                }

                // Load payout history to preserve rotation order.
                List<Payout> pastPayouts =
                    findPastPayouts(connection);

                User recipient = payoutRule.determineNextRecipient(
                    members,
                    pastPayouts
                );

                if (recipient == null) {
                    throw new IllegalStateException(
                        "Could not determine the next payout recipient."
                    );
                }

                // Save the payout.
                insertPayout(
                    connection,
                    cycleId,
                    recipient.getId(),
                    totalAmount,
                    date
                );

                // Mark the completed cycle as paid.
                closeCycle(connection, cycleId);

                // Prepare the next open cycle.
                createNextCycle(connection);

                connection.commit();

                return new Payout(
                    recipient.getId(),
                    totalAmount,
                    date
                );

            } catch (Exception e) {

                connection.rollback();

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                throw new SQLException(
                    "Payout execution failed.", e
                );
            }
        }
    }

    /**
     * Retrieves all payouts for this group.
     * Newest payouts are returned first.
     */
    public List<Payout> findAll() throws SQLException {

        List<Payout> payouts = new ArrayList<>();

        String sql = """
            SELECT
                recipient_member_id,
                amount,
                payout_date
            FROM payouts
            WHERE group_id = ?
            ORDER BY payout_date DESC, created_at DESC
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    payouts.add(new Payout(
                        result.getObject("recipient_member_id").toString(),
                        result.getBigDecimal("amount"),
                        result.getDate("payout_date").toLocalDate()
                    ));
                }
            }
        }

        return payouts;
    }

    private void lockGroup(Connection connection)
            throws SQLException {

        String sql = """
            SELECT id
            FROM savings_groups
            WHERE id = ?
            FOR UPDATE
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new IllegalStateException(
                        "Chilimba Group was not found."
                    );
                }
            }
        }
    }

    private UUID findOpenCycle(Connection connection)
            throws SQLException {

        String sql = """
            SELECT id
            FROM contribution_cycles
            WHERE group_id = ?
              AND status = 'OPEN'
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                if (!result.next()) {
                    throw new IllegalStateException(
                        "There is no open contribution cycle."
                    );
                }

                return result.getObject("id", UUID.class);
            }
        }
    }

    private List<User> findActiveMembers(Connection connection)
            throws SQLException {

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

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {

                    String id = result.getObject("id").toString();
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

                    member.setMemberCode(
                        result.getString("member_code")
                    );

                    members.add(member);
                }
            }
        }

        return members;
    }

    private void validateMemberContributions(
            Connection connection,
            UUID cycleId,
            List<User> members) throws SQLException {

        String sql = """
            SELECT COUNT(*)
            FROM contributions
            WHERE group_id = ?
              AND cycle_id = ?
              AND member_id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            for (User member : members) {

                statement.setObject(1, GROUP_ID);
                statement.setObject(2, cycleId);
                statement.setObject(3, UUID.fromString(member.getId()));

                try (ResultSet result = statement.executeQuery()) {

                    result.next();

                    if (result.getInt(1) == 0) {
                        throw new IllegalStateException(
                            member.getName() +
                            " has not contributed in this cycle."
                        );
                    }
                }
            }
        }
    }

    private BigDecimal calculateCycleTotal(
            Connection connection,
            UUID cycleId) throws SQLException {

        String sql = """
            SELECT COALESCE(SUM(amount), 0) AS total
            FROM contributions
            WHERE group_id = ?
              AND cycle_id = ?
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);
            statement.setObject(2, cycleId);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getBigDecimal("total");
            }
        }
    }

    private List<Payout> findPastPayouts(Connection connection)
            throws SQLException {

        List<Payout> payouts = new ArrayList<>();

        String sql = """
            SELECT
                recipient_member_id,
                amount,
                payout_date
            FROM payouts
            WHERE group_id = ?
            ORDER BY created_at ASC, id ASC
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    payouts.add(new Payout(
                        result.getObject("recipient_member_id").toString(),
                        result.getBigDecimal("amount"),
                        result.getDate("payout_date").toLocalDate()
                    ));
                }
            }
        }

        return payouts;
    }

    private void insertPayout(
            Connection connection,
            UUID cycleId,
            String recipientId,
            BigDecimal amount,
            LocalDate date) throws SQLException {

        String sql = """
            INSERT INTO payouts (
                group_id,
                cycle_id,
                recipient_member_id,
                amount,
                payout_date,
                recorded_by_user_id
            )
            VALUES (
                ?, ?, ?, ?, ?,
                (SELECT id FROM users WHERE email = ?)
            )
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);
            statement.setObject(2, cycleId);
            statement.setObject(3, UUID.fromString(recipientId));
            statement.setBigDecimal(4, amount);
            statement.setObject(5, date);
            statement.setString(6, SYSTEM_USER_EMAIL);

            int rows = statement.executeUpdate();

            if (rows != 1) {
                throw new SQLException(
                    "Payout record was not inserted."
                );
            }
        }
    }

    private void closeCycle(
            Connection connection,
            UUID cycleId) throws SQLException {

        String sql = """
            UPDATE contribution_cycles
            SET status = 'PAID',
                closed_at = CURRENT_TIMESTAMP
            WHERE id = ?
              AND group_id = ?
              AND status = 'OPEN'
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, cycleId);
            statement.setObject(2, GROUP_ID);

            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException(
                    "The current cycle could not be closed."
                );
            }
        }
    }

    private void createNextCycle(Connection connection)
            throws SQLException {

        String sql = """
            SELECT COALESCE(MAX(cycle_number), 0) + 1
                   AS next_number
            FROM contribution_cycles
            WHERE group_id = ?
            """;

        int nextNumber;

        try (PreparedStatement statement =
                connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {
                result.next();
                nextNumber = result.getInt("next_number");
            }
        }

        String insertSql = """
            INSERT INTO contribution_cycles (
                group_id,
                cycle_number,
                status
            )
            VALUES (?, ?, 'OPEN')
            """;

        try (PreparedStatement statement =
                connection.prepareStatement(insertSql)) {

            statement.setObject(1, GROUP_ID);
            statement.setInt(2, nextNumber);

            if (statement.executeUpdate() != 1) {
                throw new SQLException(
                    "The next contribution cycle could not be created."
                );
            }
        }
    }
}