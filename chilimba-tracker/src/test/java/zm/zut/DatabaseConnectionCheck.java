package zm.zut;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

public class DatabaseConnectionCheck {

    public static void main(String[] args) {

        System.out.println("Connecting to Neon PostgreSQL...");

        try (Connection connection = Database.getConnection();
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT current_database(), current_user")) {

            if (result.next()) {
                System.out.println("Connection successful!");
                System.out.println("Database: " + result.getString(1));
                System.out.println("User: " + result.getString(2));
            }

        } catch (Exception e) {
            System.err.println("Connection test failed.");
            System.err.println("Error: " + e.getMessage());
            System.exit(1);
        }
    }
}
