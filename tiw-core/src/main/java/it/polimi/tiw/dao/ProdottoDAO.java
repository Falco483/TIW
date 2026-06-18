package it.polimi.tiw.dao;

import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.ProdottoComposto;
import it.polimi.tiw.model.ProdottoSemplice;
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

/**
 * Classe DAO per l'interazione con la tabella prodotto del database
 * Gestisce le operazioni di CRUD (Create, Read, Update, Delete) sui prodotti
 * e l'interazione con le tabelle correlate sku e prodotto_sku
 */
public class ProdottoDAO {

    private final Connection connection;

    public ProdottoDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Recupera tutti i prodotti radice (che non hanno un padre) di tipo COMPOSTO.
     * 
     * @return Lista di prodotti radice.
     */
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

    /**
     * Conta il numero totale di prodotti di tipo COMPOSTO memorizzati nel database.
     * Utilizzato principalmente per la paginazione nel pannello del fornitore.
     *
     * @return il numero totale di prodotti composti.
     * @throws SQLException se la query SQL fallisce.
     */
    public int contaProdottiComposti() throws SQLException {
        String sql = "SELECT COUNT(*) FROM prodotto WHERE tipo = 'COMPOSTO' AND id_padre IS NULL";
        try (PreparedStatement stmt = connection.prepareStatement(sql);
                ResultSet rs = stmt.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    /**
     * Estrae una lista di prodotti composti paginati e ordinati per nome in ordine
     * decrescente.
     *
     * @param offset l'indice di partenza dei risultati da restituire (salta le
     *               prime N righe).
     * @param limit  il numero massimo di prodotti da restituire nella pagina.
     * @return una lista di ProdottoComposto.
     * @throws SQLException se la query SQL fallisce.
     */
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

    /**
     * Recupera tutti i prodotti (sia semplici che composti) ordinati per nome in
     * modo decrescente.
     * Usato per popolare le checkbox e i form di selezione nel pannello del
     * fornitore.
     *
     * @return la lista di tutti i prodotti.
     * @throws SQLException se la query SQL fallisce.
     */
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

    /**
     * Recupera tutti i prodotti orfani (cioè senza un prodotto padre, id_padre IS
     * NULL)
     * la cui profondità complessiva del sotto-albero non supera 2 livelli.
     * Questi prodotti sono candidabili come figli per nuovi prodotti composti.
     *
     * @return una lista di prodotti orfani idonei.
     * @throws SQLException se la query SQL fallisce.
     */
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
                Prodotto p = mapRow(rs);
                if (calcolaProfondita(p.getId()) <= 2) {
                    risultati.add(p);
                }
            }
        }
        return risultati;
    }

    /**
     * Cerca un prodotto per il suo ID univoco (chiave primaria).
     *
     * @param id l'ID del prodotto da trovare.
     * @return il prodotto trovato, oppure null se non esiste.
     * @throws SQLException se la query SQL fallisce.
     */
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

    /**
     * Cerca un prodotto partendo dal suo codice univoco di business.
     *
     * @param codice il codice del prodotto da trovare.
     * @return il prodotto trovato, oppure null se non esiste.
     * @throws SQLException se la query SQL fallisce.
     */
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

    /**
     * Carica l'intero albero di un prodotto (composto o semplice) partendo dal suo
     * ID.
     * Se il prodotto è composto carica ricorsivamente tutti i figli, altrimenti
     * carica le SKU associate.
     *
     * @param id l'ID del prodotto radice.
     * @return l'oggetto Prodotto completo del sotto-albero.
     * @throws SQLException se la query SQL fallisce.
     */
    public Prodotto getAlberoProdotto(int id) throws SQLException {
        Prodotto root = findById(id);
        if (root instanceof ProdottoComposto pc) {
            caricaFigli(pc);
        } else if (root instanceof ProdottoSemplice ps) {
            caricaSku(ps);
        }
        return root;
    }

    /**
     * Carica l'intero albero di un prodotto (composto o semplice) partendo dal suo
     * codice.
     * Se è composto, carica ricorsivamente i figli. Se è semplice, carica le SKU
     * associate.
     * 
     * @param codice Codice identificativo del prodotto.
     * @return L'oggetto prodotto completo di sotto-albero.
     * @throws SQLException se la query SQL fallisce.
     */
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

    /**
     * Calcola il livello di annidamento (profondità dall'alto) di un prodotto.
     * La radice è al livello 1, i suoi figli diretti sono al livello 2, ecc.
     * Restituisce 0 se il prodotto non esiste.
     *
     * @param idProdotto l'ID del prodotto.
     * @return il livello del prodotto (1 per la radice), oppure 0.
     * @throws SQLException se la query SQL fallisce.
     */
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

    /**
     * Verifica che non vengano creati cicli all'interno dell'albero dei prodotti.
     * Un ciclo si verificherebbe se il padre proposto è già un discendente del
     * figlio proposto.
     *
     * @param idPadre  l'ID del padre proposto.
     * @param idFiglio l'ID del figlio proposto.
     * @return true se l'operazione non crea cicli (cioè è aciclica), false
     *         altrimenti.
     * @throws SQLException se la query SQL fallisce.
     */
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

    /**
     * Calcola la profondità massima del sotto-albero radicato nel prodotto dato
     * (navigazione verso il basso). Ritorna 1 se il nodo è una foglia.
     *
     * @param idProdotto l'ID del prodotto radice del sotto-albero.
     * @return la profondità del sotto-albero.
     * @throws SQLException se la query SQL fallisce.
     */
    public int calcolaProfondita(int idProdotto) throws SQLException {
        String sql = """
                WITH RECURSIVE discendenti AS (
                    SELECT id, 1 AS livello FROM prodotto WHERE id = ?
                    UNION ALL
                    SELECT p.id, d.livello + 1
                    FROM prodotto p JOIN discendenti d ON p.id_padre = d.id
                )
                SELECT MAX(livello) FROM discendenti
                """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idProdotto);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 1;
            }
        }
    }

    /**
     * Conta il numero di SKU associate a un prodotto semplice.
     * Utile per validare che un prodotto semplice abbia almeno una SKU.
     *
     * @param idProdotto l'ID del prodotto semplice.
     * @return il numero di SKU associate.
     * @throws SQLException se la query SQL fallisce.
     */
    public int contaSkuAssociate(int idProdotto) throws SQLException {
        String sql = "SELECT COUNT(*) FROM prodotto_sku WHERE id_prodotto = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idProdotto);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    // -------------------------------------------------------------------------
    // Insert
    // -------------------------------------------------------------------------

    /**
     * Inserisce un prodotto semplice con i prezzi min e max calcolati dalla servlet
     * a partire dalle SKU selezionate nel form (MIN e MAX dei prezzi delle SKU
     * scelte).
     *
     * @param codice    il codice a barre/identificativo di business del prodotto.
     * @param nome      il nome del prodotto.
     * @param prezzoMin il prezzo minimo calcolato.
     * @param prezzoMax il prezzo massimo calcolato.
     * @return l'ID auto-generato del prodotto semplice.
     * @throws SQLException se l'inserimento o il recupero delle chiavi fallisce.
     */
    public int insertSemplice(String codice, String nome, BigDecimal prezzoMin, BigDecimal prezzoMax)
            throws SQLException {
        String sql = "INSERT INTO prodotto (codice, nome, tipo, prezzo_min, prezzo_max) VALUES (?, ?, 'SEMPLICE', ?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setString(1, codice);
            stmt.setString(2, nome);
            stmt.setBigDecimal(3, prezzoMin);
            stmt.setBigDecimal(4, prezzoMax);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next())
                    return keys.getInt(1);
                throw new SQLException("Insert prodotto semplice non ha restituito un id generato");
            }
        }
    }

    /**
     * Inserisce un prodotto composto con la sua descrizione e la fascia di prezzo.
     *
     * @param codice      il codice identificativo di business del prodotto.
     * @param nome        il nome del prodotto composto.
     * @param descrizione la descrizione descrittiva dei componenti inclusi.
     * @param prezzoMin   il prezzo minimo della fascia consentita.
     * @param prezzoMax   il prezzo massimo della fascia consentita.
     * @return l'ID auto-generato del prodotto composto.
     * @throws SQLException se la query SQL di inserimento fallisce.
     */
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
                if (keys.next())
                    return keys.getInt(1);
                throw new SQLException("Insert prodotto composto non ha restituito un id generato");
            }
        }
    }

    // -------------------------------------------------------------------------
    // Relazioni
    // -------------------------------------------------------------------------

    /**
     * Associa una SKU a un prodotto semplice inserendo una riga nella tabella
     * prodotto_sku.
     * Utilizza la clausola INSERT IGNORE per evitare errori in caso di associazione
     * duplicata.
     *
     * @param idProdotto l'ID del prodotto semplice.
     * @param idSku      l'ID della SKU da associare.
     * @throws SQLException se la query SQL fallisce.
     */
    public void addSku(int idProdotto, int idSku) throws SQLException {
        // INSERT IGNORE: se la coppia (id_prodotto, id_sku) esiste già (PK composita),
        // l'operazione viene ignorata silenziosamente senza lanciare una duplicate-key
        // exception.
        String sql = "INSERT IGNORE INTO prodotto_sku (id_prodotto, id_sku) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idProdotto);
            stmt.setInt(2, idSku);
            stmt.executeUpdate();
        }
    }

    /**
     * Imposta il prodotto padre per un determinato prodotto figlio.
     * 
     * @param idPadre  l'ID del prodotto padre (deve essere COMPOSTO).
     * @param idFiglio l'ID del prodotto figlio.
     * @throws SQLException          se il figlio non viene trovato o se la query
     *                               fallisce.
     * @throws IllegalStateException se il figlio ha già un padre diverso impostato.
     */
    public void addFiglio(int idPadre, int idFiglio) throws SQLException {
        Prodotto figlio = findById(idFiglio);
        if (figlio == null)
            throw new SQLException("Prodotto figlio non trovato: " + idFiglio);
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

    /**
     * Carica ricorsivamente tutti i figli di un prodotto composto.
     * Per ogni figlio, se è composto continua la ricorsione, se è semplice carica
     * le SKU.
     * 
     * @param padre Il prodotto composto di cui caricare i componenti.
     */
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

    /**
     * Carica tutte le SKU associate a un prodotto semplice dalla tabella
     * prodotto_sku.
     *
     * @param prodottoSemplice il prodotto semplice da arricchire con le sue SKU.
     * @throws SQLException se la query fallisce.
     */
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

    /**
     * Converte una riga del ResultSet in un oggetto Prodotto (Semplice o Composto).
     * 
     * @param rs Il ResultSet corrente.
     * @return L'istanza corretta di Prodotto.
     */
    private Prodotto mapRow(ResultSet rs) throws SQLException {
        Prodotto p;
        if ("COMPOSTO".equals(rs.getString("tipo"))) {
            ProdottoComposto pc = new ProdottoComposto();
            pc.setDescrizione(rs.getString("descrizione"));
            p = pc;
        } else {
            p = new ProdottoSemplice();
        }
        p.setId(rs.getInt("id"));
        p.setCodice(rs.getInt("codice"));
        p.setNome(rs.getString("nome"));
        p.setTipo(rs.getString("tipo"));
        // prezzoMin e prezzoMax letti per entrambi i tipi (SEMPLICE: calcolati dalle
        // SKU al momento della creazione;
        // COMPOSTO: scelti dal fornitore come somma dei prezzi dei sotto-prodotti)
        p.setPrezzoMin(rs.getBigDecimal("prezzo_min"));
        p.setPrezzoMax(rs.getBigDecimal("prezzo_max"));
        int idPadre = rs.getInt("id_padre");
        p.setIdPadre(rs.wasNull() ? null : idPadre);
        return p;
    }

    // -------------------------------------------------------------------------
    // Ricerca e Gestione Fornitore
    // -------------------------------------------------------------------------

    /**
     * Esegue una ricerca testuale (LIKE) su nome e descrizione di tutti i prodotti.
     *
     * @param query la stringa di ricerca inserita dall'utente.
     * @return una lista di ElementoCatalogo che contengono la stringa cercata.
     * @throws SQLException se la query SQL fallisce.
     */
    public List<ElementoCatalogo> search(String query) throws SQLException {
        String sql = """
                SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max
                FROM prodotto
                WHERE nome LIKE ? OR descrizione LIKE ?
                ORDER BY nome ASC
                """;
        List<ElementoCatalogo> risultati = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            String like = "%" + query + "%";
            stmt.setString(1, like);
            stmt.setString(2, like);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ElementoCatalogo ec = new ElementoCatalogo();
                    ec.setId(rs.getInt("id"));
                    ec.setCodice(rs.getInt("codice"));
                    ec.setNome(rs.getString("nome"));
                    ec.setTipo(rs.getString("tipo"));
                    ec.setDescrizione(rs.getString("descrizione"));
                    ec.setPrezzoMin(rs.getBigDecimal("prezzo_min"));
                    ec.setPrezzoMax(rs.getBigDecimal("prezzo_max"));
                    risultati.add(ec);
                }
            }
        }
        return risultati;
    }

    /**
     * Ricalcola la fascia di prezzo (minimo e massimo) di un prodotto semplice
     * basandosi sui prezzi delle sue SKU associate correnti.
     *
     * @param idProdotto l'ID del prodotto semplice da aggiornare.
     * @throws SQLException se la query SQL fallisce.
     */
    public void calcolaPrezziDaSku(int idProdotto) throws SQLException {
        String sql = """
                UPDATE prodotto
                SET prezzo_min = (SELECT MIN(s.prezzo) FROM sku s JOIN prodotto_sku ps ON s.id = ps.id_sku WHERE ps.id_prodotto = ?),
                    prezzo_max = (SELECT MAX(s.prezzo) FROM sku s JOIN prodotto_sku ps ON s.id = ps.id_sku WHERE ps.id_prodotto = ?)
                WHERE id = ?
                """;
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idProdotto);
            stmt.setInt(2, idProdotto);
            stmt.setInt(3, idProdotto);
            stmt.executeUpdate();
        }
    }

    /**
     * Rimuove l'associazione N:M tra un prodotto semplice e una determinata SKU.
     *
     * @param idProdotto l'ID del prodotto semplice.
     * @param idSku      l'ID della SKU da dissociare.
     * @throws SQLException se la query SQL fallisce.
     */
    public void rimuoviAssociazioneSku(int idProdotto, int idSku) throws SQLException {
        String sql = "DELETE FROM prodotto_sku WHERE id_prodotto = ? AND id_sku = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idProdotto);
            stmt.setInt(2, idSku);
            stmt.executeUpdate();
        }
    }

    /**
     * Scollega un prodotto figlio dal suo prodotto padre (imposta id_padre a NULL).
     *
     * @param idFiglio l'ID del prodotto figlio da scollegare.
     * @throws SQLException se la query SQL fallisce.
     */
    public void rimuoviFiglio(int idFiglio) throws SQLException {
        String sql = "UPDATE prodotto SET id_padre = NULL WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idFiglio);
            stmt.executeUpdate();
        }
    }

    /**
     * Ricalcola la fascia di prezzo (minimo e massimo) di un prodotto composto
     * basandosi sulla somma dei prezzi min/max dei suoi figli diretti correnti.
     * Se il prodotto non ha più figli, imposta entrambi i prezzi a 0.
     *
     * @param idPadre l'ID del prodotto composto da aggiornare.
     * @throws SQLException se la query SQL fallisce.
     */
    public void ricalcolaPrezziComposto(int idPadre) throws SQLException {
        String selectSql = "SELECT SUM(prezzo_min), SUM(prezzo_max) FROM prodotto WHERE id_padre = ?";
        BigDecimal min = BigDecimal.ZERO;
        BigDecimal max = BigDecimal.ZERO;
        try (PreparedStatement stmt = connection.prepareStatement(selectSql)) {
            stmt.setInt(1, idPadre);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    BigDecimal sumMin = rs.getBigDecimal(1);
                    BigDecimal sumMax = rs.getBigDecimal(2);
                    if (sumMin != null)
                        min = sumMin;
                    if (sumMax != null)
                        max = sumMax;
                }
            }
        }

        String updateSql = "UPDATE prodotto SET prezzo_min = ?, prezzo_max = ? WHERE id = ? AND tipo = 'COMPOSTO'";
        try (PreparedStatement stmt = connection.prepareStatement(updateSql)) {
            stmt.setBigDecimal(1, min);
            stmt.setBigDecimal(2, max);
            stmt.setInt(3, idPadre);
            stmt.executeUpdate();
        }
    }

    /**
     * Elimina definitivamente un prodotto e tutta la sua sotto-gerarchia.
     * Prima rimuove le configurazioni i cui dettagli referenziano
     * questo prodotto o suoi discendenti (per evitare violazione
     * del vincolo RESTRICT su configurazione_dettaglio).
     * 
     * @param id l'ID del prodotto radice da eliminare.
     * @throws SQLException se l'eliminazione fallisce.
     */
    public void eliminaDefinitivamente(int id) throws SQLException {
        boolean autoCommitOriginale = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            // 1) Trova le configurazioni il cui dettaglio referenzia
            // questo prodotto o un suo discendente, e cancellale.
            String findConfigSql = """
                    WITH RECURSIVE discendenti AS (
                        SELECT id FROM prodotto WHERE id = ?
                        UNION ALL
                        SELECT p.id FROM prodotto p JOIN discendenti d ON p.id_padre = d.id
                    )
                    SELECT DISTINCT cd.id_configurazione
                    FROM configurazione_dettaglio cd
                    WHERE cd.id_prodotto IN (SELECT id FROM discendenti)
                    """;
            try (PreparedStatement stmt = connection.prepareStatement(findConfigSql)) {
                stmt.setInt(1, id);
                try (ResultSet rs = stmt.executeQuery()) {
                    while (rs.next()) {
                        int idConfig = rs.getInt(1);
                        try (PreparedStatement del = connection.prepareStatement(
                                "DELETE FROM configurazione WHERE id = ?")) {
                            del.setInt(1, idConfig);
                            del.executeUpdate();
                        }
                    }
                }
            }

            // 2) Ora cancella il prodotto (i figli cascadano via ON DELETE CASCADE)
            String sql = "DELETE FROM prodotto WHERE id = ?";
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
     * Aggiorna i campi modificabili di un prodotto (nome, descrizione, prezzoMin e
     * prezzoMax).
     *
     * @param p l'oggetto Prodotto contenente i nuovi valori.
     * @throws SQLException se la query SQL fallisce.
     */
    public void updateProdotto(Prodotto p) throws SQLException {
        String sql = "UPDATE prodotto SET nome = ?, descrizione = ?, prezzo_min = ?, prezzo_max = ? WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, p.getNome());
            stmt.setString(2, p.getDescrizione());
            stmt.setBigDecimal(3, p.getPrezzoMin());
            stmt.setBigDecimal(4, p.getPrezzoMax());
            stmt.setInt(5, p.getId());
            stmt.executeUpdate();
        }
    }

    // -------------------------------------------------------------------------
    // Inserimento Ricorsivo Transazionale (SPA)
    // -------------------------------------------------------------------------

    /**
     * Salva un intero albero di prodotti in modo atomico all'interno di una
     * transazione.
     * Esegue l'inserimento partendo dal nodo radice e scendendo ricorsivamente
     * lungo i rami.
     *
     * @param radice il prodotto composto che fa da radice all'albero.
     * @return l'ID auto-generato del prodotto radice inserito.
     * @throws SQLException se una delle operazioni di inserimento fallisce.
     */
    public int insertTree(ProdottoComposto radice) throws SQLException {
        boolean autoCommitOriginale = connection.getAutoCommit();
        try {
            connection.setAutoCommit(false);

            int rootId = insertNode(radice, null);

            connection.commit();
            return rootId;
        } catch (SQLException e) {
            connection.rollback();
            throw e;
        } finally {
            connection.setAutoCommit(autoCommitOriginale);
        }
    }

    /**
     * Metodo di supporto ricorsivo privato che inserisce un nodo del prodotto e i
     * suoi figli.
     * Se il nodo è semplice, associa anche le SKU definite.
     *
     * @param nodo    il nodo corrente da inserire.
     * @param idPadre l'ID del prodotto padre (può essere null per il nodo radice).
     * @return l'ID auto-generato del nodo inserito.
     * @throws SQLException se l'inserimento fallisce.
     */
    private int insertNode(Prodotto nodo, Integer idPadre) throws SQLException {
        String sql = "INSERT INTO prodotto (codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre) VALUES (?, ?, ?, ?, ?, ?, ?)";

        int idGenerato;

        try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, nodo.getCodice());
            stmt.setString(2, nodo.getNome());

            if (nodo instanceof ProdottoComposto) {
                stmt.setString(3, "COMPOSTO");
                stmt.setString(4, nodo.getDescrizione());
                stmt.setBigDecimal(5, nodo.getPrezzoMin());
                stmt.setBigDecimal(6, nodo.getPrezzoMax());
            } else if (nodo instanceof ProdottoSemplice) {
                stmt.setString(3, "SEMPLICE");
                stmt.setNull(4, java.sql.Types.VARCHAR);
                stmt.setNull(5, java.sql.Types.DECIMAL);
                stmt.setNull(6, java.sql.Types.DECIMAL);
            } else {
                throw new SQLException("Tipo di prodotto sconosciuto.");
            }

            if (idPadre == null) {
                stmt.setNull(7, java.sql.Types.INTEGER);
            } else {
                stmt.setInt(7, idPadre);
            }

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    idGenerato = keys.getInt(1);
                    nodo.setId(idGenerato);
                } else {
                    throw new SQLException("Nessun ID generato per il nodo.");
                }
            }
        }

        if (nodo instanceof ProdottoComposto pc) {
            if (pc.getFigli() != null) {
                for (Prodotto figlio : pc.getFigli()) {
                    insertNode(figlio, idGenerato);
                }
            }
        } else if (nodo instanceof ProdottoSemplice ps) {
            if (ps.getSKUs() != null) {
                for (SKU sku : ps.getSKUs()) {
                    addSku(idGenerato, sku.getId());
                }
            }
        }

        return idGenerato;
    }
}
