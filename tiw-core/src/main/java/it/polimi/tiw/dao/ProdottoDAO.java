package it.polimi.tiw.dao;

import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.ProdottoComposto;
import it.polimi.tiw.model.ProdottoSemplice;
import it.polimi.tiw.model.SKU;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class ProdottoDAO {

    private final Connection connection;

    public ProdottoDAO(Connection connection) {
        this.connection = connection;
    }

    // -------------------------------------------------------------------------
    // Query
    // -------------------------------------------------------------------------

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
            return rs.next() ? rs.getInt(1) : 0;
        }
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

    // Tutti i prodotti (semplici + composti) ordinati per nome decrescente.
    // Usato per popolare le checkboxes nel form "Crea Prodotto Composto".
    public List<Prodotto> findAll() throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto
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

    // Tutti i prodotti senza padre (orfani), usati come candidati figli nel form "Crea Prodotto Composto".
    public List<Prodotto> findAllOrfani() throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto
            WHERE id_padre IS NULL
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

    public Prodotto findById(int id) throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto WHERE id = ?
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, id);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Prodotto findByCodice(int codice) throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto WHERE codice = ?
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, codice);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Prodotto getAlberoProdotto(int id) throws SQLException {
        Prodotto root = findById(id);
        if (root instanceof ProdottoComposto pc) {
            caricaFigli(pc);
        } else if (root instanceof ProdottoSemplice ps) {
            caricaSku(ps);
        }
        return root;
    }

    public Prodotto getAlberoProdottoByCodice(int codice) throws SQLException {
        Prodotto root = findByCodice(codice);
        if (root instanceof ProdottoComposto pc) {
            caricaFigli(pc);
        } else if (root instanceof ProdottoSemplice ps) {
            caricaSku(ps);
        }
        return root;
    }

    // -------------------------------------------------------------------------
    // Vincoli di dominio
    // -------------------------------------------------------------------------

    // Ritorna il livello del prodotto (1 = radice). 0 se non trovato.
    public int calcolaLivello(int idProdotto) throws SQLException {
        String sql = """
            WITH RECURSIVE antenati AS (
                SELECT id, id_padre, 1 AS livello
                FROM prodotto WHERE id = ?
                UNION ALL
                SELECT p.id, p.id_padre, a.livello + 1
                FROM prodotto p JOIN antenati a ON p.id = a.id_padre
            )
            SELECT MAX(livello) FROM antenati
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idProdotto);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // True se aggiungere idFiglio come figlio di idPadre NON crea un ciclo.
    // Un ciclo si crea se idPadre è già discendente di idFiglio.
    public boolean verificaAciclicita(int idPadre, int idFiglio) throws SQLException {
        String sql = """
            WITH RECURSIVE antenati AS (
                SELECT id, id_padre FROM prodotto WHERE id = ?
                UNION ALL
                SELECT p.id, p.id_padre
                FROM prodotto p JOIN antenati a ON p.id = a.id_padre
            )
            SELECT COUNT(*) FROM antenati WHERE id = ?
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idPadre);
            stmt.setInt(2, idFiglio);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() && rs.getInt(1) == 0;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    public int insertSemplice(String codice, String nome) throws SQLException {
        String sql = "INSERT INTO prodotto (codice, nome, tipo) VALUES (?, ?, 'SEMPLICE')";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, codice);
            stmt.setString(2, nome);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert prodotto semplice non ha restituito un id generato");
            }
        }
    }

    public int insertComposto(String codice, String nome, String descrizione,
                              BigDecimal prezzoMin, BigDecimal prezzoMax) throws SQLException {
        String sql = """
            INSERT INTO prodotto (codice, nome, tipo, descrizione, prezzo_min, prezzo_max)
            VALUES (?, ?, 'COMPOSTO', ?, ?, ?)
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, codice);
            stmt.setString(2, nome);
            stmt.setString(3, descrizione);
            stmt.setBigDecimal(4, prezzoMin);
            stmt.setBigDecimal(5, prezzoMax);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) return keys.getInt(1);
                throw new SQLException("Insert prodotto composto non ha restituito un id generato");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Relazioni
    // -------------------------------------------------------------------------

    public void addSku(int idProdotto, int idSku) throws SQLException {
        String sql = "INSERT INTO prodotto_sku (id_prodotto, id_sku) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idProdotto);
            stmt.setInt(2, idSku);
            stmt.executeUpdate();
        }
    }

    // Setta id_padre sul figlio (il figlio entra nella gerarchia del padre).
    // Lancia IllegalStateException se il figlio ha già un padre diverso.
    public void addFiglio(int idPadre, int idFiglio) throws SQLException {
        Prodotto figlio = findById(idFiglio);
        if (figlio == null) throw new SQLException("Prodotto figlio non trovato: " + idFiglio);
        if (figlio.getIdPadre() != null) {
            throw new IllegalStateException(
                "Il prodotto " + idFiglio + " appartiene già a un altro padre");
        }
        String sql = "UPDATE prodotto SET id_padre = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idPadre);
            stmt.setInt(2, idFiglio);
            stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Caricamento ricorsivo (privato)
    // -------------------------------------------------------------------------

    private void caricaFigli(ProdottoComposto padre) throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto WHERE id_padre = ?
            """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, padre.getId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Prodotto figlio = mapRow(rs);
                    padre.addFiglio(figlio);
                    if (figlio instanceof ProdottoComposto pc) {
                        caricaFigli(pc);
                    } else if (figlio instanceof ProdottoSemplice ps) {
                        caricaSku(ps);
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
                    SKU sku = new SKU();
                    sku.setId(rs.getInt("id"));
                    sku.setCodice(rs.getInt("codice"));
                    sku.setNome(rs.getString("nome"));
                    sku.setFotografia(rs.getString("fotografia"));
                    sku.setDescrizioneTecnica(rs.getString("descrizione_tecnica"));
                    sku.setPrezzo(rs.getBigDecimal("prezzo"));
                    prodottoSemplice.addSKU(sku);
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Mapping
    // -------------------------------------------------------------------------

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
        p.setCodice(rs.getInt("codice"));
        p.setNome(rs.getString("nome"));
        p.setTipo(rs.getString("tipo"));
        int idPadre = rs.getInt("id_padre");
        p.setIdPadre(rs.wasNull() ? null : idPadre);
        return p;
    }
}
