import java.sql.*;
public class TestDB {
    public static void main(String[] args) throws Exception {
        Connection conn = DriverManager.getConnection("jdbc:mysql://127.0.0.1:3306/tiw_db?serverTimezone=UTC", "root", "root1234");
        Statement stmt = conn.createStatement();
        ResultSet rs2 = stmt.executeQuery("SELECT * FROM prodotto_sku WHERE id_prodotto IN (52, 20, 34, 53)");
        while(rs2.next()) {
            System.out.println("SKU found for product ID: " + rs2.getInt("id_prodotto"));
        }
        conn.close();
    }
}
