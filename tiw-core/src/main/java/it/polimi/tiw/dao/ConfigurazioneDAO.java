package it.polimi.tiw.dao;

import it.polimi.tiw.dto.DettaglioDTO;
import it.polimi.tiw.model.Configurazione;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

public class ConfigurazioneDAO {
    private final Connection connection;

    public ConfigurazioneDAO(Connection connection) {
        this.connection = connection;
    }

    public int inserisciTestata(Configurazione conf) throws SQLException {
        String sql = "INSERT INTO configurazione (cliente_username, prodotto_radice_id, nome, prezzo_totale) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, conf.getClienteUsername());
            stmt.setInt(2, conf.getProdottoRadiceId());
            stmt.setString(3, conf.getNome());
            stmt.setBigDecimal(4, conf.getPrezzoTotale());
            
            stmt.executeUpdate();
            
            try (ResultSet generatedKeys = stmt.getGeneratedKeys()) {
                if (generatedKeys.next()) {
                    return generatedKeys.getInt(1);
                } else {
                    throw new SQLException("Creating configuration failed, no ID obtained.");
                }
            }
        }
    }

    public void inserisciDettagliBatch(int idConfig, List<DettaglioDTO> dettagli) throws SQLException {
        String sql = "INSERT INTO configurazione_dettaglio (id_configurazione, id_prodotto, id_sku, prezzo_unitario_congelato) VALUES (?, ?, ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            for (DettaglioDTO d : dettagli) {
                stmt.setInt(1, idConfig);
                stmt.setInt(2, d.getIdProdotto());
                stmt.setInt(3, d.getIdSku());
                stmt.setBigDecimal(4, d.getPrezzoUnitarioCongelato());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
    }
}
