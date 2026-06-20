package it.polimi.tiw.dao;

import it.polimi.tiw.model.SKU;
import it.polimi.tiw.model.ElementoCatalogo;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class SKUDAO {
    private final Connection connection;

    /**
     * Costruttore del DAO.
     *
     * @param connection connessione JDBC attiva.
     */
    public SKUDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Recupera tutte le SKU memorizzate nel database, ordinate per codice decrescente.
     *
     * @return la lista di tutte le SKU.
     * @throws SQLException se la query SQL fallisce.
     */
    public List<SKU> findAll() throws SQLException {
        String sql = "SELECT id, codice, nome, fotografia, descrizione_tecnica, prezzo FROM sku ORDER BY codice DESC";
        List<SKU> skus = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                skus.add(mapRow(rs));
            }
        }
        return skus;
    }

    /**
     * Recupera una SKU specifica per il suo ID univoco (chiave primaria).
     *
     * @param id l'ID della SKU.
     * @return la SKU trovata, o null se non esiste.
     * @throws SQLException se la query SQL fallisce.
     */
    public SKU findById(int id) throws SQLException {
        String sql = "SELECT id, codice, nome, fotografia, descrizione_tecnica, prezzo FROM sku WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * Recupera una SKU specifica a partire dal suo codice identificativo di business.
     *
     * @param codice il codice della SKU.
     * @return la SKU trovata, o null se non esiste.
     * @throws SQLException se la query SQL fallisce.
     */
    public SKU findByCodice(int codice) throws SQLException {
        String sql = "SELECT id, codice, nome, fotografia, descrizione_tecnica, prezzo FROM sku WHERE codice = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, codice);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    /**
     * Inserisce una nuova SKU nel database.
     * Recupera la chiave auto-generata generata da MySQL e la imposta sull'oggetto SKU passato.
     *
     * @param sku l'oggetto SKU con i dati da salvare.
     * @return l'oggetto SKU salvato, completo di ID.
     * @throws SQLException se l'inserimento o la generazione dell'ID fallisce.
     */
    public SKU insert(SKU sku) throws SQLException {
        String sql = "INSERT INTO sku (codice, nome, fotografia, descrizione_tecnica, prezzo) VALUES (?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sku.getCodice());
            ps.setString(2, sku.getNome());
            ps.setString(3, sku.getFotografia());
            ps.setString(4, sku.getDescrizioneTecnica());
            ps.setBigDecimal(5, sku.getPrezzo());
            
            ps.executeUpdate();
            
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    sku.setId(keys.getInt(1));
                    return sku;
                }
                throw new SQLException("Insert SKU non ha restituito un id generato");
            }
        }
    }

    /**
     * Recupera il prezzo corrente di una SKU. Serve a "congelare" nella
     * configurazione il prezzo attuale del catalogo al momento del salvataggio.
     *
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

    /**
     * Converte una riga del ResultSet in un oggetto SKU.
     *
     * @param rs il ResultSet posizionato sulla riga corrente.
     * @return un oggetto SKU.
     * @throws SQLException se la lettura dei dati fallisce.
     */
    private SKU mapRow(ResultSet rs) throws SQLException {
        SKU sku = new SKU();
        sku.setId(rs.getInt("id"));
        sku.setCodice(rs.getInt("codice"));
        sku.setNome(rs.getString("nome"));
        sku.setFotografia(rs.getString("fotografia"));
        sku.setDescrizioneTecnica(rs.getString("descrizione_tecnica"));
        sku.setPrezzo(rs.getBigDecimal("prezzo"));
        return sku;
    }

    // -------------------------------------------------------------------------
    // Ricerca e Gestione Fornitore
    // -------------------------------------------------------------------------

    /**
     * Cerca SKU per nome o descrizione tecnica con una LIKE.
     * I risultati sono mappati su ElementoCatalogo, per uniformarli a quelli
     * della ricerca prodotti.
     *
     * @param query la stringa di ricerca.
     * @return la lista degli elementi del catalogo trovati.
     * @throws SQLException se la query SQL fallisce.
     */
    public List<ElementoCatalogo> search(String query) throws SQLException {
        String sql = "SELECT id, codice, nome, descrizione_tecnica, prezzo FROM sku WHERE nome LIKE ? OR descrizione_tecnica LIKE ? ORDER BY nome ASC";
        List<ElementoCatalogo> risultati = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + query + "%";
            ps.setString(1, like);
            ps.setString(2, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    ElementoCatalogo ec = new ElementoCatalogo();
                    ec.setId(rs.getInt("id"));
                    ec.setCodice(rs.getInt("codice"));
                    ec.setNome(rs.getString("nome"));
                    ec.setTipo("SKU");
                    ec.setDescrizione(rs.getString("descrizione_tecnica"));
                    ec.setPrezzoMin(rs.getBigDecimal("prezzo"));
                    ec.setPrezzoMax(rs.getBigDecimal("prezzo"));
                    risultati.add(ec);
                }
            }
        }
        return risultati;
    }

    /**
     * Elimina definitivamente una SKU e tutte le configurazioni dei clienti
     * ad essa collegate, per mantenere l'integrità referenziale.
     *
     * @param id l'ID della SKU da eliminare.
     * @throws SQLException se la cancellazione fallisce.
     */
    public void eliminaDefinitivamente(int id) throws SQLException {
        boolean autoCommitOriginale = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);
            
            // Trova e cancella tutte le configurazioni che usano questa SKU
            String findConfigSql = "SELECT DISTINCT id_configurazione FROM configurazione_dettaglio WHERE id_sku = ?";
            try (PreparedStatement stmt = connection.prepareStatement(findConfigSql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int idConfigurazione = rs.getInt(1);
                        String delConfigSql = "DELETE FROM configurazione WHERE id = ?";
                        try (PreparedStatement delStmt = connection.prepareStatement(delConfigSql)) {
                            delStmt.setInt(1, idConfigurazione);
                            delStmt.executeUpdate();
                        }
                    }
                }
            }
            
            // Cancella la SKU (le associazioni in prodotto_sku vengono rimosse in automatico via CASCADE)
            String sql = "DELETE FROM sku WHERE id = ?";
            try (PreparedStatement stmt = connection.prepareStatement(sql)) {
                stmt.setInt(1, id);
                stmt.executeUpdate();
            }
            
            connection.commit();
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommitOriginale);
        }
    }

    /**
     * Aggiorna le informazioni di una SKU nel database (nome, fotografia, descrizione tecnica e prezzo).
     *
     * @param sku l'oggetto SKU con le modifiche da applicare.
     * @throws SQLException se l'aggiornamento fallisce.
     */
    public void update(SKU sku) throws SQLException {
        String sql = "UPDATE sku SET nome = ?, fotografia = ?, descrizione_tecnica = ?, prezzo = ? WHERE id = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setString(1, sku.getNome());
            ps.setString(2, sku.getFotografia());
            ps.setString(3, sku.getDescrizioneTecnica());
            ps.setBigDecimal(4, sku.getPrezzo());
            ps.setInt(5, sku.getId());
            ps.executeUpdate();
        }
    }

    /**
     * Conta a quanti prodotti semplici è associata una SKU (righe in prodotto_sku).
     *
     * @param idSku l'ID della SKU.
     * @return il numero di associazioni trovate.
     * @throws SQLException se la query SQL fallisce.
     */
    public int countUsage(int idSku) throws SQLException {
        String sql = "SELECT COUNT(*) FROM prodotto_sku WHERE id_sku = ?";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setInt(1, idSku);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }
}
