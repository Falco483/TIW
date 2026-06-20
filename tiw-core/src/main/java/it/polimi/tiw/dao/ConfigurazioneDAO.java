package it.polimi.tiw.dao;

import it.polimi.tiw.dto.DettaglioDTO;
import it.polimi.tiw.dto.VoceConfigurazioneDTO;
import it.polimi.tiw.model.Configurazione;
import it.polimi.tiw.model.SKU;

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
 * DAO per le tabelle {@code configurazione} e {@code configurazione_dettaglio}.
 *
 * I metodi che modificano dati filtrano sempre per {@code cliente_username}
 * nella WHERE, così un utente può operare solo sulle proprie configurazioni.
 */
public class ConfigurazioneDAO {
    private final Connection connection;

    public ConfigurazioneDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Inserisce la testata di una nuova configurazione. Le date sono gestite
     * dai DEFAULT del DB. Restituisce l'ID auto-generato, che serve poi per
     * collegare i dettagli.
     *
     * @param conf dati della configurazione da inserire
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
     * Inserisce in batch le righe di dettaglio di una configurazione.
     *
     * Il campo {@code prezzo_unitario_congelato} salva il prezzo della SKU al
     * momento del salvataggio, così il totale della configurazione non cambia
     * se in seguito il catalogo viene aggiornato.
     *
     * @param idConfig ID della configurazione padre
     * @param dettagli righe da inserire (id_prodotto, id_sku, prezzo congelato)
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
     * Elimina una configurazione solo se appartiene all'utente indicato.
     * I dettagli associati vengono rimossi dal DB grazie al ON DELETE CASCADE.
     *
     * @param idConfig ID della configurazione da eliminare
     * @param username cliente loggato, per la verifica di proprietà
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
     * Recupera la testata di una configurazione, dato il suo ID e l'username
     * proprietario. Usata sia in clonazione sia nel GET di modifica, dove serve
     * anche a verificare la proprietà prima di mostrare il form.
     *
     * @param idConfig ID della configurazione da recuperare
     * @param username proprietario atteso
     * @return la Configurazione, oppure null se non esiste o non appartiene all'utente
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
     * Restituisce le scelte salvate in una configurazione come mappa
     * id_prodotto → id_sku. Usata nel form di modifica per pre-selezionare le
     * SKU scelte in precedenza.
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
     * Aggiorna nome e prezzo totale di una configurazione esistente, solo se
     * appartiene all'utente. Il campo {@code data_modifica} è aggiornato dal DB
     * (ON UPDATE CURRENT_TIMESTAMP).
     *
     * @param conf configurazione con id, clienteUsername, nome e prezzoTotale aggiornati
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
     * Elimina tutti i dettagli di una configurazione. Usato nella modifica con
     * pattern "delete + re-insert": si cancellano i vecchi dettagli e si
     * reinseriscono con {@link #inserisciDettagliBatch}, il tutto dentro la
     * stessa transazione.
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
     * Recupera le voci di dettaglio di una configurazione (prodotti semplici e
     * relative SKU associate),
     * includendo il prezzo della SKU "congelato" al momento del salvataggio.
     *
     * @param idConfig ID della configurazione di cui recuperare i dettagli.
     * @return Mappa che associa l'ID del Prodotto Semplice a un oggetto DTO
     *         contenente la SKU e il prezzo congelato.
     * @throws SQLException se la query fallisce.
     */
    public Map<Integer, VoceConfigurazioneDTO> getVociDettaglio(int idConfig) throws SQLException {
        String sql = "SELECT cd.id_prodotto, cd.prezzo_unitario_congelato, "
                + "s.id, s.codice, s.nome, s.fotografia, s.descrizione_tecnica, s.prezzo "
                + "FROM configurazione_dettaglio cd "
                + "JOIN sku s ON cd.id_sku = s.id "
                + "WHERE cd.id_configurazione = ?";
        Map<Integer, VoceConfigurazioneDTO> mappa = new HashMap<>();
        try (PreparedStatement stmt = connection.prepareStatement(sql)) {
            stmt.setInt(1, idConfig);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    SKU sku = new SKU();
                    sku.setId(rs.getInt("id"));
                    sku.setCodice(rs.getInt("codice"));
                    sku.setNome(rs.getString("nome"));
                    sku.setFotografia(rs.getString("fotografia"));
                    sku.setDescrizioneTecnica(rs.getString("descrizione_tecnica"));
                    sku.setPrezzo(rs.getBigDecimal("prezzo"));
                    mappa.put(rs.getInt("id_prodotto"),
                            new VoceConfigurazioneDTO(sku, rs.getBigDecimal("prezzo_unitario_congelato")));
                }
            }
        }
        return mappa;
    }

    /**
     * Restituisce le configurazioni di un utente, dalla più recente alla meno
     * recente. Fa un JOIN con {@code prodotto} per riempire i campi transienti
     * nomeProdottoRadice e codiceProdottoRadice, usati nella lista e nei link di
     * modifica.
     *
     * @param username cliente di cui recuperare le configurazioni
     * @return lista ordinata per data_modifica DESC, vuota se nessuna trovata
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

    /**
     * Elimina tutte le configurazioni che contengono un dato componente (SKU o
     * prodotto). Va chiamata prima di rimuovere il componente dal catalogo, per
     * non violare i vincoli RESTRICT su configurazione_dettaglio.
     *
     * @param idComponente ID del componente da cercare nei dettagli
     * @param tipo         "SKU" oppure "PRODOTTO" (case insensitive)
     * @throws SQLException se la query DELETE fallisce
     */
    public void eliminaConfigurazioniPerComponente(int idComponente, String tipo) throws SQLException {
        String colonna = tipo.equalsIgnoreCase("SKU") ? "id_sku" : "id_prodotto";

        // Cancellando la testata, i dettagli spariscono in cascata (ON DELETE CASCADE).
        String sql = "DELETE FROM configurazione WHERE id IN (SELECT id_configurazione FROM configurazione_dettaglio WHERE "
                + colonna + " = ?)";

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, idComponente);
            pstmt.executeUpdate();
        }
    }
}
