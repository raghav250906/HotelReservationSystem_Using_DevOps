
import java.sql.Connection;

public class DatabaseTest {

    public static void main(String[] args) {

        try (Connection con = DBConnection.getConnection()) {

            System.out.println(
                "MySQL Connected Successfully!"
            );

        } catch (Exception e) {

            System.out.println(
                "Database Connection Failed!"
            );

            e.printStackTrace();
        }
    }
}
