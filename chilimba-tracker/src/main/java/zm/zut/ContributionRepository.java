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

public class ContributionRepository {

    private static final UUID GROUP_ID = UUID.fromString(
        "2e2067d4-6ca4-4cef-ba6f-98f335d52df7"
    );

    private static final String SYSTEM_USER_EMAIL =
        "system@chilimba.local";

    /**
     * Records a contribution in the current open cycle.
     *
     * Multiple contributions from the same member are allowed.
     */
    public Contribution recordContribution(
            String memberId,
            BigDecimal amount,
            LocalDate date) throws SQLException {

        if (memberId == null || memberId.isBlank()) {
            throw new IllegalArgumentException(
                "Member ID is required."
            );
        }

        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                "Contribution amount must be greater than zero."
            );
        }

        LocalDate contributionDate =
            date == null ? LocalDate.now() : date;

        UUID memberUuid;

        try {
            memberUuid = UUID.fromString(memberId);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                "Invalid member ID."
            );
        }

        try (Connection connection = Database.getConnection()) {

            connection.setAutoCommit(false);

            try {
                // Lock the group to coordinate contributions
                // with other financial operations.
                String lockSql = """
                    SELECT id
                    FROM savings_groups
                    WHERE id = ?
                    FOR UPDATE
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(lockSql)) {

                    statement.setObject(1, GROUP_ID);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException(
                                "Chilimba Group was not found."
                            );
                        }
                    }
                }

                // Confirm that the member is active in this group.
                String memberSql = """
                    SELECT id
                    FROM group_members
                    WHERE id = ?
                      AND group_id = ?
                      AND status = 'ACTIVE'
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(memberSql)) {

                    statement.setObject(1, memberUuid);
                    statement.setObject(2, GROUP_ID);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalArgumentException(
                                "Active member was not found in this group."
                            );
                        }
                    }
                }

                // Find the current open cycle.
                UUID cycleId;

                String cycleSql = """
                    SELECT id
                    FROM contribution_cycles
                    WHERE group_id = ?
                      AND status = 'OPEN'
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(cycleSql)) {

                    statement.setObject(1, GROUP_ID);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new IllegalStateException(
                                "There is no open contribution cycle."
                            );
                        }

                        cycleId = result.getObject("id", UUID.class);
                    }
                }

                // Insert the contribution.
                // The system account acts as the temporary recorder
                // until user authentication is implemented.
                String insertSql = """
                    INSERT INTO contributions (
                        group_id,
                        cycle_id,
                        member_id,
                        amount,
                        contribution_date,
                        recorded_by_user_id
                    )
                    VALUES (
                        ?, ?, ?, ?, ?,
                        (SELECT id FROM users WHERE email = ?)
                    )
                    RETURNING member_id, amount, contribution_date
                    """;

                Contribution savedContribution;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertSql)) {

                    statement.setObject(1, GROUP_ID);
                    statement.setObject(2, cycleId);
                    statement.setObject(3, memberUuid);
                    statement.setBigDecimal(4, amount);
                    statement.setObject(5, contributionDate);
                    statement.setString(6, SYSTEM_USER_EMAIL);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                "Contribution could not be saved. " +
                                "Check that the system account exists."
                            );
                        }

                        savedContribution = new Contribution(
                            result.getObject("member_id").toString(),
                            result.getBigDecimal("amount"),
                            result.getDate("contribution_date").toLocalDate()
                        );
                    }
                }

                connection.commit();
                return savedContribution;

            } catch (Exception e) {

                connection.rollback();

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                throw new SQLException(
                    "Could not record contribution.", e
                );
            }
        }
    }

    /**
     * Retrieves all contributions in the current open cycle.
     */
    public List<Contribution> findAllForCurrentCycle()
            throws SQLException {

        List<Contribution> contributions = new ArrayList<>();

        String sql = """
            SELECT
                c.member_id,
                c.amount,
                c.contribution_date
            FROM contributions c
            JOIN contribution_cycles cy
              ON cy.id = c.cycle_id
             AND cy.group_id = c.group_id
            WHERE c.group_id = ?
              AND cy.status = 'OPEN'
            ORDER BY c.contribution_date ASC, c.created_at ASC
            """;

        try (Connection connection = Database.getConnection();
             PreparedStatement statement =
                 connection.prepareStatement(sql)) {

            statement.setObject(1, GROUP_ID);

            try (ResultSet result = statement.executeQuery()) {

                while (result.next()) {
                    contributions.add(new Contribution(
                        result.getObject("member_id").toString(),
                        result.getBigDecimal("amount"),
                        result.getDate("contribution_date").toLocalDate()
                    ));
                }
            }
        }

        return List.copyOf(contributions);
    }
}