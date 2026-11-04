package zm.zut;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

public class CycleRepository {

    private static final UUID GROUP_ID = UUID.fromString(
        "2e2067d4-6ca4-4cef-ba6f-98f335d52df7"
    );

    /**
     * Returns the current open cycle.
     * Creates a new cycle if no open cycle exists.
     */
    public UUID getOrCreateOpenCycle() throws SQLException {

        try (Connection connection = Database.getConnection()) {

            connection.setAutoCommit(false);

            try {
                // Lock the group to prevent competing cycle creation.
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

                // Check for an existing open cycle.
                String findSql = """
                    SELECT id
                    FROM contribution_cycles
                    WHERE group_id = ?
                      AND status = 'OPEN'
                    """;

                try (PreparedStatement statement =
                        connection.prepareStatement(findSql)) {

                    statement.setObject(1, GROUP_ID);

                    try (ResultSet result = statement.executeQuery()) {
                        if (result.next()) {
                            UUID cycleId =
                                result.getObject("id", UUID.class);

                            connection.commit();
                            return cycleId;
                        }
                    }
                }

                // Find the next cycle number.
                String numberSql = """
                    SELECT COALESCE(MAX(cycle_number), 0) + 1
                           AS next_number
                    FROM contribution_cycles
                    WHERE group_id = ?
                    """;

                int nextNumber;

                try (PreparedStatement statement =
                        connection.prepareStatement(numberSql)) {

                    statement.setObject(1, GROUP_ID);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                "Could not determine the next cycle number."
                            );
                        }

                        nextNumber = result.getInt("next_number");
                    }
                }

                // Create the next open cycle.
                String insertSql = """
                    INSERT INTO contribution_cycles (
                        group_id,
                        cycle_number,
                        status
                    )
                    VALUES (?, ?, 'OPEN')
                    RETURNING id
                    """;

                UUID newCycleId;

                try (PreparedStatement statement =
                        connection.prepareStatement(insertSql)) {

                    statement.setObject(1, GROUP_ID);
                    statement.setInt(2, nextNumber);

                    try (ResultSet result = statement.executeQuery()) {
                        if (!result.next()) {
                            throw new SQLException(
                                "Cycle creation returned no record."
                            );
                        }

                        newCycleId =
                            result.getObject("id", UUID.class);
                    }
                }

                connection.commit();
                return newCycleId;

            } catch (Exception e) {

                connection.rollback();

                if (e instanceof SQLException sqlException) {
                    throw sqlException;
                }

                if (e instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }

                throw new SQLException(
                    "Could not create or retrieve an open cycle.", e
                );
            }
        }
    }
}