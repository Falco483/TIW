package it.polimi.tiw.dao;

import it.polimi.tiw.dto.DettaglioDTO;
import it.polimi.tiw.model.Configurazione;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data Access Object per la tabella {@code configurazione} e {@code configurazione_dettaglio}.
 *
 * Gestisce tutte le operazioni CRUD sulle configurazioni del cliente:
 * inserimento, lettura, aggiornamento, eliminazione, clonazione e
 * recupero dei dettagli (scelte SKU) associati.
 *
 * Ogni metodo che modifica dati protegge la proprietà della risorsa
 * filtrando per {@code cliente_username} nella clausola WHERE.
 */
public class ConfigurazioneDAO {
    private final Connection connection;

    /**
     * Costruttore: riceve la connessione JDBC da usare per tutte le query.
     * La connessione NON viene chiusa da questo DAO — la gestione del ciclo
     * di vita è responsabilità del chiamante (Servlet).
     *
     * @param connection connessione JDBC attiva verso il database MySQL
     */
    public ConfigurazioneDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserisce la testata (riga padre) di una nuova configurazione nella tabella {@code configurazione}.
     *
     * Campi inseriti: cliente_username, prodotto_radice_id, nome, prezzo_totale.
     * I campi data_creazione e data_modifica sono gestiti dal DEFAULT del DB (CURRENT_TIMESTAMP).
     *
     * Usa {@code Statement.RETURN_GENERATED_KEYS} per recuperare l'ID auto-generato
     * dalla colonna AUTO_INCREMENT, necessario per collegare i dettagli (righe figlie).
     *
     * @param conf oggetto Configurazione con i dati da inserire
     * @return l'ID auto-generato della nuova configurazione
     * @throws SQLException se l'inserimento fallisce o non viene generato un ID
     */
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

    /**
     * Inserisce in batch tutte le righe di dettaglio nella tabella {@code configurazione_dettaglio}.
     *
     * Ogni riga rappresenta la scelta di una SKU per un prodotto semplice dell'albero.
     * Il campo {@code prezzo_unitario_congelato} contiene il prezzo corrente della SKU
     * al momento del salvataggio (Price Snapshotting), così il prezzo della configurazione
     * non cambia se il catalogo viene aggiornato in seguito.
     *
     * Usa JDBC batch ({@code addBatch/executeBatch}) per efficienza: una sola
     * round-trip al DB invece di N INSERT separati.
     *
     * @param idConfig ID della configurazione padre (FK verso configurazione.id)
     * @param dettagli lista di DTO contenenti id_prodotto, id_sku e prezzo congelato
     * @throws SQLException se l'inserimento batch fallisce
     */
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

    /**
     * Elimina una configurazione solo se appartiene all'utente specificato.
     *
     * La clausola {@code WHERE id = ? AND cliente_username = ?} garantisce che
     * un utente non possa cancellare le configurazioni altrui (sicurezza per proprietà).
     * Grazie al {@code ON DELETE CASCADE} sulla FK di configurazione_dettaglio,
     * i dettagli associati vengono eliminati automaticamente dal DB.
     *
     * @param idConfig ID della configurazione da eliminare
     * @param username username del cliente loggato (verifica proprietà)
     * @throws SQLException se la query DELETE fallisce
     */
    public void eliminaConfigurazione(int idConfig, String username) throws SQLException {
        String sql = "DELETE FROM configurazione WHERE id = ? AND cliente_username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idConfig);
            stmt.setString(2, username);
            stmt.executeUpdate();
        }
    }

    /**
     * Recupera la testata di una configurazione dato il suo ID e l'username proprietario.
     *
     * Usato in due contesti:
     * - Clonazione: per leggere nome e prodotto_radice_id della configurazione originale.
     * - Modifica (GET): per verificare che la configurazione appartenga all'utente loggato
     *   prima di mostrare il form pre-compilato.
     *
     * Restituisce {@code null} se la configurazione non esiste oppure non appartiene
     * all'utente specificato (doppia protezione: 404 + autorizzazione).
     *
     * @param idConfig ID della configurazione da recuperare
     * @param username username del proprietario atteso
     * @return l'oggetto Configurazione completo, oppure null se non trovata/non autorizzata
     * @throws SQLException se la query SELECT fallisce
     */
    public Configurazione getConfigurazioneById(int idConfig, String username) throws SQLException {
        String sql = "SELECT id, cliente_username, prodotto_radice_id, nome, data_creazione, data_modifica, prezzo_totale "
                   + "FROM configurazione WHERE id = ? AND cliente_username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idConfig);
            stmt.setString(2, username);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Configurazione c = new Configurazione();
                    c.setId(rs.getInt("id"));
                    c.setClienteUsername(rs.getString("cliente_username"));
                    c.setProdottoRadiceId(rs.getInt("prodotto_radice_id"));
                    c.setNome(rs.getString("nome"));
                    c.setDataCreazione(rs.getTimestamp("data_creazione").toLocalDateTime());
                    c.setDataModifica(rs.getTimestamp("data_modifica").toLocalDateTime());
                    c.setPrezzoTotale(rs.getBigDecimal("prezzo_totale"));
                    return c;
                }
            }
        }
        return null;
    }

    /**
     * Restituisce le scelte SKU salvate in una configurazione come mappa prodotto → SKU.
     *
     * Interroga la tabella {@code configurazione_dettaglio} e costruisce una
     * {@code Map<Integer, Integer>} dove:
     * - chiave = id_prodotto (il prodotto semplice dell'albero)
     * - valore = id_sku (la variante scelta dal cliente per quel prodotto)
     *
     * Usata nel flusso di modifica: il template Thymeleaf usa questa mappa
     * per impostare {@code th:selected} sulle option della select, così l'utente
     * vede le scelte precedenti già selezionate quando apre il form.
     *
     * @param idConfig ID della configurazione di cui recuperare i dettagli
     * @return mappa id_prodotto → id_sku con le scelte salvate
     * @throws SQLException se la query SELECT fallisce
     */
    public Map<Integer, Integer> getScelteDettaglio(int idConfig) throws SQLException {
        String sql = "SELECT id_prodotto, id_sku FROM configurazione_dettaglio WHERE id_configurazione = ?";
        Map<Integer, Integer> scelte = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idConfig);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    scelte.put(rs.getInt("id_prodotto"), rs.getInt("id_sku"));
                }
            }
        }
        return scelte;
    }

    /**
     * Aggiorna la testata di una configurazione esistente (nome e prezzo totale).
     *
     * Esegue un UPDATE sulla tabella {@code configurazione} modificando solo
     * {@code nome} e {@code prezzo_totale}. Il campo {@code data_modifica} si
     * aggiorna automaticamente grazie alla clausola {@code ON UPDATE CURRENT_TIMESTAMP}
     * definita nello schema SQL, quindi non serve settarlo esplicitamente.
     *
     * La clausola {@code WHERE id = ? AND cliente_username = ?} impedisce che
     * un utente modifichi configurazioni altrui.
     *
     * @param conf oggetto Configurazione con id, clienteUsername, nome e prezzoTotale aggiornati
     * @throws SQLException se la query UPDATE fallisce
     */
    public void updateTestata(Configurazione conf) throws SQLException {
        String sql = "UPDATE configurazione SET nome = ?, prezzo_totale = ? WHERE id = ? AND cliente_username = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, conf.getNome());
            stmt.setBigDecimal(2, conf.getPrezzoTotale());
            stmt.setInt(3, conf.getId());
            stmt.setString(4, conf.getClienteUsername());
            stmt.executeUpdate();
        }
    }

    /**
     * Elimina tutti i dettagli (righe figlie) di una configurazione.
     *
     * Usato nel flusso di modifica con pattern "delete + re-insert":
     * 1. Si cancellano TUTTI i vecchi dettagli con questo metodo
     * 2. Si reinseriscono i nuovi dettagli con {@link #inserisciDettagliBatch}
     *
     * Questo approccio è più semplice e sicuro rispetto a un UPDATE selettivo
     * riga per riga, ed è comunque atomico perché avviene dentro una transazione
     * con {@code setAutoCommit(false)}.
     *
     * @param idConfig ID della configurazione di cui eliminare i dettagli
     * @throws SQLException se la query DELETE fallisce
     */
    public void deleteDettagli(int idConfig) throws SQLException {
        String sql = "DELETE FROM configurazione_dettaglio WHERE id_configurazione = ?";
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idConfig);
            stmt.executeUpdate();
        }
    }

    /**
     * Restituisce tutte le configurazioni salvate da un utente, ordinate per data
     * di modifica decrescente (le più recenti prima).
     *
     * Esegue un JOIN con la tabella {@code prodotto} per recuperare anche il nome
     * e il codice del prodotto radice, necessari per visualizzare la lista e
     * costruire i link di modifica (che richiedono il codice prodotto come parametro).
     *
     * I campi {@code nomeProdottoRadice} e {@code codiceProdottoRadice} sono campi
     * transienti del model Configurazione (non mappati su colonne proprie della tabella
     * configurazione, ma popolati dal risultato del JOIN).
     *
     * @param username username del cliente di cui recuperare le configurazioni
     * @return lista di Configurazione ordinate per data_modifica DESC, vuota se nessuna trovata
     * @throws SQLException se la query SELECT fallisce
     */
    public List<Configurazione> getConfigurazioniByUtente(String username) throws SQLException {
        String sql = "SELECT c.id, c.cliente_username, c.prodotto_radice_id, c.nome, "
                   + "c.data_creazione, c.data_modifica, c.prezzo_totale, p.nome AS nome_prodotto, p.codice AS codice_prodotto "
                   + "FROM configurazione c "
                   + "JOIN prodotto p ON c.prodotto_radice_id = p.id "
                   + "WHERE c.cliente_username = ? "
                   + "ORDER BY c.data_modifica DESC";
        List<Configurazione> lista = new ArrayList<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setString(1, username);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    Configurazione c = new Configurazione();
                    c.setId(rs.getInt("id"));
                    c.setClienteUsername(rs.getString("cliente_username"));
                    c.setProdottoRadiceId(rs.getInt("prodotto_radice_id"));
                    c.setNome(rs.getString("nome"));
                    c.setDataCreazione(rs.getTimestamp("data_creazione").toLocalDateTime());
                    c.setDataModifica(rs.getTimestamp("data_modifica").toLocalDateTime());
                    c.setPrezzoTotale(rs.getBigDecimal("prezzo_totale"));
                    c.setNomeProdottoRadice(rs.getString("nome_prodotto"));
                    c.setCodiceProdottoRadice(rs.getInt("codice_prodotto"));
                    lista.add(c);
                }
            }
        }
        return lista;
    }
}
