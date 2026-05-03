package it.polimi.tiw.servlet.api;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.model.Prodotto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * ============================================================================
 * ApiProdottoController — Controller per la versione SPA (JSON API)
 * ============================================================================
 *
 * Mappato su "/api/prodotti" (endpoint REST-ish per la SPA JavaScript).
 *
 * Questo controller fa TRE cose e basta:
 *   1. Ottiene la connessione al DB
 *   2. Chiama LO STESSO DAO usato da WebProdottoController
 *   3. Serializza il risultato in JSON con Jackson e lo scrive nel body
 *
 * NOTA SUL DRY (Don't Repeat Yourself):
 *   Guarda il metodo doGet(). La riga critica è:
 *
 *       List<Prodotto> prodotti = dao.getProdottiRadice();
 *
 *   È IDENTICA a quella in WebProdottoController.
 *   La query SQL sta nel DAO. La logica di accesso ai dati sta nel DAO.
 *   Se domani aggiungiamo un filtro alla query (es: paginazione), lo
 *   modifichiamo SOLO nel DAO → entrambi i controller ne beneficiano
 *   automaticamente.
 *
 *   Se avessimo messo la query SQL dentro le Servlet (errore classico
 *   del Junior), avremmo la stessa query copiata in DUE posti.
 *   La prima volta che le modifichi e dimentichi di aggiornare l'altra,
 *   hai un bug fantasma che appare solo in una delle due versioni.
 *
 * NOTA SU JACKSON ObjectMapper:
 *   ObjectMapper è THREAD-SAFE e COSTOSO da creare (reflection, cache interna).
 *   Lo creiamo UNA volta come campo statico e lo riusiamo in tutte le richieste.
 *   Crearlo in ogni doGet() sarebbe un performance killer su un server con
 *   centinaia di richieste al secondo.
 * ============================================================================
 */
@WebServlet("/api/prodotti")
public class ApiProdottoController extends HttpServlet {

    /**
     * ObjectMapper condiviso. Thread-safe per design (documentato da Jackson).
     * Configurato una volta sola all'avvio della Servlet.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())                      // supporto LocalDateTime
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // ISO-8601, non array di numeri

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // Header di risposta: SEMPRE impostati PRIMA di scrivere il body.
        // Se li imposti dopo, Tomcat potrebbe aver già inviato gli header
        // con valori di default (text/html) → il client interpreta male.
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Connection conn = null;

        try {
            conn = getServletContext().getAttribute("dbConnection") != null
                    ? (Connection) getServletContext().getAttribute("dbConnection")
                    : null;

            if (conn == null) {
                sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Connessione al DB non disponibile");
                return;
            }

            // -----------------------------------------------------------------
            // LO STESSO DAO. LA STESSA CHIAMATA. Output diverso.
            // Questa è la Separation of Concerns in azione.
            // -----------------------------------------------------------------
            ProdottoDAO dao = new ProdottoDAO(conn);
            List<Prodotto> prodotti = dao.getProdottiRadice();

            // -----------------------------------------------------------------
            // Risposta JSON con wrapper "data" (API contract standard).
            // Il client JavaScript farà:
            //   const resp = await fetch('/api/prodotti');
            //   const { data } = await resp.json();
            //   data.forEach(prodotto => renderCard(prodotto));
            // -----------------------------------------------------------------
            Map<String, Object> payload = Map.of("data", prodotti);
            MAPPER.writeValue(response.getOutputStream(), payload);

        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel recupero dei prodotti");
        }
    }

    /**
     * Helper per inviare errori JSON strutturati.
     * Rispetta il contratto API: { "errore": "messaggio" }
     */
    private void sendError(HttpServletResponse response, int status, String messaggio)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), Map.of("errore", messaggio));
    }
}
