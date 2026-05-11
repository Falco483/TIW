package it.polimi.tiw.dao;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO per la gestione delle SKU (varianti concrete dei prodotti).
 * Utilizzato principalmente per recuperare i prezzi correnti dal catalogo.
 */
public class SkuDAO {
    private final Connection connection;

    /**
     * Costruttore del DAO.
     * @param connection La connessione al database.
     */
    public SkuDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Recupera il prezzo corrente di una specifica SKU dal database.
     * Fondamentale per il "Price Snapshotting": assicura che il prezzo salvato
     * nella configurazione sia quello attuale del catalogo.
     * @param idSku ID della SKU.
     * @return Il prezzo come BigDecimal, o null se la SKU non esiste.
     */
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
