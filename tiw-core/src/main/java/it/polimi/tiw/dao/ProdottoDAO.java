package it.polimi.tiw.dao;

import it.polimi.tiw.model.Prodotto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ProdottoDAO — Data Access Object per la tabella `prodotto`.
 * Riceve Connection via costruttore (DI manuale). NON la chiude.
 * Usato identicamente da WebProdottoController e ApiProdottoController.
 */
public class ProdottoDAO {

    private final Connection connection;

    public ProdottoDAO(Connection connection) {
        this.connection = connection;
    }

    public List<Prodotto> getProdottiRadice() throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto
            WHERE id_padre IS NULL AND tipo = 'COMPOSTO'
            ORDER BY nome DESC
            """;

        List<Prodotto> risultati = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                risultati.add(mapRow(rs));
            }
        }
        return risultati;
    }

    private Prodotto mapRow(ResultSet rs) throws SQLException {
        Prodotto p = new Prodotto();
        p.setId(rs.getInt("id"));
        p.setCodice(rs.getString("codice"));
        p.setNome(rs.getString("nome"));
        p.setTipo(rs.getString("tipo"));
        p.setDescrizione(rs.getString("descrizione"));
        p.setPrezzoMin(rs.getBigDecimal("prezzo_min"));
        p.setPrezzoMax(rs.getBigDecimal("prezzo_max"));
        int idPadre = rs.getInt("id_padre");
        p.setIdPadre(rs.wasNull() ? null : idPadre);
        return p;
    }
}
