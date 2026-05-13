package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.dto.DettaglioDTO;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.model.Configurazione;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Servlet per le azioni rapide sulle configurazioni: Cancellazione e
 * Clonazione.
 *
 * Cancellazione: Elimina una configurazione esistente.
 * Clonazione: Crea una copia esatta di una configurazione, ricalcolando i
 * prezzi dal catalogo corrente.
 *
 * Entrambe le azioni sono protette dal token CSRF e verificano la proprietà
 * della risorsa.
 */
@WebServlet("/cliente/azione")
public class ConfigurazioneActionServlet extends HttpServlet {

    /**
	 * 
	 */
	private Connection connection = null;

    /**
     * Inizializza la servlet stabilendo la connessione al database.
     */
    @Override
    public void init() throws ServletException {
        try {
            connection = it.polimi.tiw.utils.ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
        }
    }

    /**
     * Chiude la connessione al database.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
        }
    }

    /**
     * Gestisce le richieste POST, smistando tra eliminazione e clonazione in base
     * al parametro 'azione'.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");

        String azione = request.getParameter("azione");
        String idStr = request.getParameter("id");

        if (azione == null || idStr == null || idStr.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametri mancanti");
            return;
        }

        int idConfig;
        try {
            idConfig = Integer.parseInt(idStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID configurazione non valido");
            return;
        }

        Connection conn = this.connection;
        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No DB connection");
            return;
        }

        // Smistamento dell'azione
        switch (azione) {
            case "elimina":
                gestisciEliminazione(conn, idConfig, utente.username(), request, response);
                break;
            case "clona":
                gestisciClonazione(conn, idConfig, utente.username(), request, response);
                break;
            default:
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Azione non riconosciuta");
        }
    }

    /**
     * Esegue l'eliminazione fisica della configurazione e dei suoi dettagli (via
     * cascade).
     */
    private void gestisciEliminazione(Connection conn, int idConfig, String username,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            ConfigurazioneDAO cDao = new ConfigurazioneDAO(conn);
            cDao.eliminaConfigurazione(idConfig, username);
            response.sendRedirect(request.getContextPath() + "/cliente/configurazioni");
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore durante l'eliminazione");
        }
    }

    /**
     * Esegue la clonazione di una configurazione esistente in modo transazionale.
     * Legge i componenti originali, recupera i prezzi attuali dal catalogo e crea
     * una nuova configurazione "Copia di...".
     */
    private void gestisciClonazione(Connection conn, int idConfig, String username,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            // Inizio transazione per garantire l'atomicità tra testata e dettagli
            conn.setAutoCommit(false);

            ConfigurazioneDAO cDao = new ConfigurazioneDAO(conn);
            SKUDAO sDao = new SKUDAO(conn);

            // 1. Verifica che la configurazione originale esista e appartenga all'utente
            Configurazione originale = cDao.getConfigurazioneById(idConfig, username);
            if (originale == null) {
                conn.rollback();
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Configurazione non trovata o non di proprietà");
                return;
            }

            // 2. Recupera le scelte SKU salvate nella configurazione originale
            Map<Integer, Integer> scelteOriginali = cDao.getScelteDettaglio(idConfig);
            if (scelteOriginali.isEmpty()) {
                conn.rollback();
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Configurazione originale senza dettagli");
                return;
            }

            // 3. Ricalcola i prezzi correnti (Price Snapshotting aggiornato)
            List<DettaglioDTO> nuoviDettagli = new ArrayList<>();
            BigDecimal nuovoPrezzoTotale = BigDecimal.ZERO;

            for (Map.Entry<Integer, Integer> entry : scelteOriginali.entrySet()) {
                int idProdotto = entry.getKey();
                int idSku = entry.getValue();

                BigDecimal prezzoCorrente = sDao.getPrezzoReale(idSku);
                if (prezzoCorrente == null) {
                    conn.rollback();
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                            "Una delle SKU non è più disponibile nel catalogo");
                    return;
                }

                nuoviDettagli.add(new DettaglioDTO(idProdotto, idSku, prezzoCorrente));
                nuovoPrezzoTotale = nuovoPrezzoTotale.add(prezzoCorrente);
            }

            // 4. Inserisce la nuova testata (padre)
            Configurazione copia = new Configurazione();
            copia.setClienteUsername(username);
            copia.setProdottoRadiceId(originale.getProdottoRadiceId());
            copia.setNome("Copia di " + originale.getNome());
            copia.setPrezzoTotale(nuovoPrezzoTotale);

            int nuovoId = cDao.inserisciTestata(copia);

            // 5. Inserisce i nuovi dettagli in batch
            cDao.inserisciDettagliBatch(nuovoId, nuoviDettagli);

            // Fine transazione
            conn.commit();
            response.sendRedirect(request.getContextPath() + "/cliente/configurazioni");

        } catch (SQLException e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore durante la clonazione");
        } finally {
            try {
                if (conn != null)
                    conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
