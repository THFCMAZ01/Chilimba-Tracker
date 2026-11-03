package zm.zut;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

public final class Database {

    private static final Map<String, String> CONFIG = loadConfig();

    private Database() {
        // Utility class
    }

    private static Map<String, String> loadConfig() {
        Map<String, String> config = new HashMap<>();

        Path envFile = Path.of(".env");

        // Load local .env file if it exists.
        if (Files.exists(envFile)) {
            try {
                for (String line : Files.readAllLines(envFile)) {
                    String trimmed = line.trim();

                    if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                        continue;
                    }

                    int separator = trimmed.indexOf('=');

                    if (separator <= 0) {
                        continue;
                    }

                    String key = trimmed.substring(0, separator).trim();
                    String value = trimmed.substring(separator + 1).trim();

                    // Remove surrounding single or double quotes.
                    if (value.length() >= 2) {
                        char first = value.charAt(0);
                        char last = value.charAt(value.length() - 1);

                        if ((first == '"' && last == '"')
                                || (first == '\'' && last == '\'')) {
                            value = value.substring(1, value.length() - 1);
                        }
                    }

                    config.put(key, value);
                }
            } catch (IOException e) {
                throw new IllegalStateException(
                        "Could not read the .env configuration file.", e);
            }
        }

        // Real environment variables take priority over .env.
        for (String key : new String[]{
                "DB_URL", "DB_USERNAME", "DB_PASSWORD"
        }) {
            String value = System.getenv(key);

            if (value != null && !value.isBlank()) {
                config.put(key, value);
            }
        }

        return config;
    }

    public static Connection getConnection() throws SQLException {
        String url = CONFIG.get("DB_URL");
        String username = CONFIG.get("DB_USERNAME");
        String password = CONFIG.get("DB_PASSWORD");

        if (isBlank(url) || isBlank(username) || isBlank(password)) {
            throw new IllegalStateException(
                    "Database configuration is incomplete. Check DB_URL, "
                    + "DB_USERNAME and DB_PASSWORD.");
        }

        return DriverManager.getConnection(url, username, password);
    }

    public static boolean testConnection() {
        try (Connection connection = getConnection()) {
            return connection.isValid(5);
        } catch (SQLException e) {
            System.err.println(
                    "Database connection failed: " + e.getMessage());
            return false;
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
