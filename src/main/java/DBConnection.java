
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {

    private static final String DB_URL =
        System.getenv().getOrDefault(
            "DB_URL",
            "jdbc:mysql://localhost:3307/hotel_db"
        );

    private static final String DB_USER =
        System.getenv("DB_USER");

    private static final String DB_PASSWORD =
        System.getenv("DB_PASSWORD");

    public static Connection getConnection()
            throws SQLException {

        if (DB_USER == null || DB_PASSWORD == null) {
            throw new SQLException(
                "Database credentials are not configured."
            );
        }

        return DriverManager.getConnection(
            DB_URL,
            DB_USER,
            DB_PASSWORD
        );
    }
}
