package it.polimi.tiw.dao;

import it.polimi.tiw.model.Prodotto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 * ProdottoDAO — Data Access Object per la tabella `prodotto`
 * ============================================================================
 *
 * REGOLE FERREE DI UN DAO:
 *
 * 1. Il DAO contiene SOLO codice SQL e mapping ResultSet → POJO.
 *    NESSUNA logica di business (es: "il prezzo è valido?").
 *    NESSUNA dipendenza da HttpServletRequest/Response/Session.
 *    NESSUNA serializzazione JSON.
 *    NESSUN riferimento a Thymeleaf.
 *
 * 2. Il DAO riceve una Connection nel costruttore (Dependency Injection
 *    manuale). NON apre e NON chiude la connessione — questo è compito
 *    del Controller/Servlet che lo crea.
 *    Perché? Perché il Controller potrebbe dover chiamare PIÙ DAO nella
 *    stessa transazione. Se ogni DAO aprisse la sua connessione, non
 *    potremmo avere transazioni ACID multi-tabella.
 *
 * 3. Il DAO usa SEMPRE PreparedStatement, MAI Statement.
 *    PreparedStatement parametrizza i valori con `?` → il driver JDBC
 *    li escapa automaticamente → SQL Injection impossibile.
 *    Statement concatena stringhe → un input malevolo come
 *    ' OR 1=1 -- diventa parte della query → SQL Injection.
 *
 * PERCHÉ ESISTE QUESTO DAO:
 *    Sia WebProdottoController (Thymeleaf) sia ApiProdottoController (JSON)
 *    hanno bisogno della stessa identica query: "dammi i prodotti radice".
 *    Senza il DAO, dovresti duplicare la query SQL in entrambe le Servlet.
 *    DRY (Don't Repeat Yourself): la logica di accesso dati sta in UN
 *    posto solo. Se domani la query cambia (es: aggiungi un filtro),
 *    la modifichi QUI e entrambi i controller ne beneficiano.
 * ============================================================================
 */
public class ProdottoDAO {

    private final Connection connection;

    /**
     * Dependency Injection manuale: il Controller passa la Connection.
     * Il DAO NON gestisce il ciclo di vita della connessione.
     */
    public ProdottoDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Restituisce i prodotti COMPOSTI di primo livello (radice).
     * Questi sono i prodotti con id_padre IS NULL e tipo = 'COMPOSTO'.
     *
     * Usata da:
     *   - WebProdottoController → per popolare la pagina "Home Cliente" (Thymeleaf)
     *   - ApiProdottoController → per rispondere a GET /api/prodotti (JSON)
     *
     * Ordinamento per nome DECRESCENTE: richiesto dalla specifica per la
     * home del cliente.
     *
     * @return Lista di prodotti composti radice, ordinati per nome DESC.
     *         Lista vuota se non ce ne sono (mai null — null è un bug).
     * @throws SQLException se la query fallisce (connessione persa, ecc.)
     */
    public List<Prodotto> getProdottiRadice() throws SQLException {
        String sql = """
            SELECT id, codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre
            FROM prodotto
            WHERE id_padre IS NULL AND tipo = 'COMPOSTO'
            ORDER BY nome DESC
            """;

        List<Prodotto> risultati = new ArrayList<>();

        // try-with-resources: chiude automaticamente PreparedStatement e ResultSet
        // anche in caso di eccezione. Senza questo, ogni eccezione causerebbe
        // un resource leak → il DB esaurisce le connessioni.
        try (PreparedStatement stmt = connection.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            while (rs.next()) {
                risultati.add(mapRow(rs));
            }
        }

        return risultati;
    }

    /**
     * Mappa una riga del ResultSet in un oggetto Prodotto.
     * Metodo privato di utilità — evita di duplicare il mapping
     * in ogni metodo del DAO (DRY applicato anche all'interno del DAO).
     */
    private Prodotto mapRow(ResultSet rs) throws SQLException {
        Prodotto p = new Prodotto();
        p.setId(rs.getInt("id"));
        p.setCodice(rs.getString("codice"));
        p.setNome(rs.getString("nome"));
        p.setTipo(rs.getString("tipo"));
        p.setDescrizione(rs.getString("descrizione"));
        p.setPrezzoMin(rs.getBigDecimal("prezzo_min"));
        p.setPrezzoMax(rs.getBigDecimal("prezzo_max"));

        // getInt ritorna 0 se il valore è NULL. Dobbiamo controllare wasNull()
        // per distinguere "id_padre = 0" (impossibile con AUTO_INCREMENT) da NULL.
        int idPadre = rs.getInt("id_padre");
        p.setIdPadre(rs.wasNull() ? null : idPadre);

        return p;
    }
}
