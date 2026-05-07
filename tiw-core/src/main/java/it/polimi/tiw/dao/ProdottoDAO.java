package it.polimi.tiw.dao;

import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.ProdottoComposto;
import it.polimi.tiw.model.ProdottoSemplice;
import it.polimi.tiw.model.Sku;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

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

    public int contaProdottiComposti() throws SQLException {
        String sql = "SELECT COUNT(*) FROM prodotto WHERE tipo = 'COMPOSTO' AND id_padre IS NULL";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        }
        return 0;
    }

    public List<ProdottoComposto> estraiProdottiCompostiPaginati(int offset, int limit) throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto
            WHERE tipo = 'COMPOSTO' AND id_padre IS NULL
            ORDER BY nome DESC
            LIMIT ? OFFSET ?
            """;

        List<ProdottoComposto> risultati = new ArrayList<>();

        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, limit);
            stmt.setInt(2, offset);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    risultati.add((ProdottoComposto) mapRow(rs));
                }
            }
        }
        return risultati;
    }

    public Prodotto getAlberoProdotto(String codice) throws SQLException {
        String rootSql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto
            WHERE codice = ?
            """;
            
        Prodotto root = null;
        try (PreparedStatement stmt = connection.prepareStatement(rootSql)) {
            stmt.setString(1, codice);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    root = mapRow(rs);
                }
            }
        }

        if (root != null && root instanceof ProdottoComposto) {
            caricaFigli((ProdottoComposto) root);
        } else if (root != null && root instanceof ProdottoSemplice) {
            caricaSku((ProdottoSemplice) root);
        }

        return root;
    }

    private void caricaFigli(ProdottoComposto padre) throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto
            WHERE id_padre = ?
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, padre.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Prodotto figlio = mapRow(rs);
                    padre.addFiglio(figlio);
                    if (figlio instanceof ProdottoComposto) {
                        caricaFigli((ProdottoComposto) figlio);
                    } else if (figlio instanceof ProdottoSemplice) {
                        caricaSku((ProdottoSemplice) figlio);
                    }
                }
            }
        }
    }

    private void caricaSku(ProdottoSemplice prodottoSemplice) throws SQLException {
        String sql = """
            SELECT s.id, s.codice, s.nome, s.fotografia, s.descrizione_tecnica, s.prezzo
            FROM sku s
            JOIN prodotto_sku ps ON s.id = ps.id_sku
            WHERE ps.id_prodotto = ?
            """;
            
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, prodottoSemplice.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Sku sku = new Sku();
                    sku.setId(rs.getInt("id"));
                    sku.setCodice(rs.getInt("codice"));
                    sku.setNome(rs.getString("nome"));
                    sku.setFotografia(rs.getString("fotografia"));
                    sku.setDescrizioneTecnica(rs.getString("descrizione_tecnica"));
                    sku.setPrezzo(rs.getBigDecimal("prezzo"));
                    prodottoSemplice.addSku(sku);
                }
            }
        }
    }

    private Prodotto mapRow(ResultSet rs) throws SQLException {
        Prodotto p;
        if ("COMPOSTO".equals(rs.getString("tipo"))) {
            ProdottoComposto pc = new ProdottoComposto();
            pc.setDescrizione(rs.getString("descrizione"));
            pc.setPrezzoMin(rs.getBigDecimal("prezzo_min"));
            pc.setPrezzoMax(rs.getBigDecimal("prezzo_max"));
            p = pc;
        } else {
            p = new ProdottoSemplice();
        }
        
        p.setId(rs.getInt("id"));
        p.setCodice(rs.getString("codice"));
        p.setNome(rs.getString("nome"));
        p.setTipo(rs.getString("tipo"));
        
        int idPadre = rs.getInt("id_padre");
        p.setIdPadre(rs.wasNull() ? null : idPadre);
        
        return p;
    }
}
