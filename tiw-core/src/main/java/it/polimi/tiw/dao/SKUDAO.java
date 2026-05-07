package it.polimi.tiw.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class SkuDAO {
    private final Connection connection;

    public SkuDAO(Connection connection) {
        this.connection = connection;
    }

    public BigDecimal getPrezzoReale(int idSku) throws SQLException {
        String sql = "SELECT prezzo FROM sku WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idSku);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBigDecimal("prezzo");
                }
            }
        }
        return null;
    }
}
