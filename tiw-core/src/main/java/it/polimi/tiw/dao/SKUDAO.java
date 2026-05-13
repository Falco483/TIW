package it.polimi.tiw.dao;

import it.polimi.tiw.model.Sku;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

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

    public List<Sku> findAll() throws SQLException {
        String sql = "SELECT id, codice, nome, fotografia, descrizione_tecnica, prezzo FROM sku ORDER BY codice DESC";
        List<Sku> skus = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                skus.add(mapRow(rs));
            }
        }
        return skus;
    }

    public Sku findById(int id) throws SQLException {
        String sql = "SELECT id, codice, nome, fotografia, descrizione_tecnica, prezzo FROM sku WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    // Ritorna l'id generato dal DB
    public int insert(int codice, String nome, String fotografia, String descrizioneTecnica, BigDecimal prezzo)
            throws SQLException {
        String sql = "INSERT INTO sku (codice, nome, fotografia, descrizione_tecnica, prezzo) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, codice);
            ps.setString(2, nome);
            ps.setString(3, fotografia);
            ps.setString(4, descrizioneTecnica);
            ps.setBigDecimal(5, prezzo);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert SKU non ha restituito un id generato");
            }
        }
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
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, idSku);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal("prezzo") : null;
            }
        }
    }

    private Sku mapRow(ResultSet rs) throws SQLException {
        Sku sku = new Sku();
        sku.setId(rs.getInt("id"));
        sku.setCodice(rs.getInt("codice"));
        sku.setNome(rs.getString("nome"));
        sku.setFotografia(rs.getString("fotografia"));
        sku.setDescrizioneTecnica(rs.getString("descrizione_tecnica"));
        sku.setPrezzo(rs.getBigDecimal("prezzo"));
        return sku;
    }
}
