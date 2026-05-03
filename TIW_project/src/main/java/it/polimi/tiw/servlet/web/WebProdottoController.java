package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.model.Prodotto;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * ============================================================================
 * WebProdottoController — Controller per la versione SSR (Thymeleaf)
 * ============================================================================
 *
 * Mappato su "/Prodotti" (versione HTML tradizionale).
 *
 * Questo controller fa TRE cose e basta (Separation of Concerns):
 *   1. Ottiene la connessione al DB
 *   2. Chiama il DAO per ottenere i dati
 *   3. Mette i dati nella request e fa forward al template Thymeleaf
 *
 * NON fa:
 *   - Query SQL (responsabilità del DAO)
 *   - Serializzazione JSON (responsabilità dell'ApiController)
 *   - Logica di business (responsabilità del DAO/Service)
 *   - Rendering HTML (responsabilità di Thymeleaf)
 *
 * PERCHÉ UNA SERVLET SEPARATA DA ApiProdottoController:
 *
 *   L'alternativa sarebbe UNA servlet con un if/else:
 *     if (request.getHeader("Accept").contains("json")) {
 *         // rispondi JSON
 *     } else {
 *         // fai forward a Thymeleaf
 *     }
 *
 *   Questo è l'anti-pattern "God Object": una classe con troppe responsabilità.
 *   Ogni modifica alla risposta JSON rischia di rompere il rendering HTML.
 *   Le due servlet hanno lifecycle diversi: l'HTML ha bisogno di forward,
 *   attributi nella request, e gestione degli errori con re-render del form.
 *   L'API ha bisogno di status code, content-type JSON, e risposta nel body.
 *   Mischiarle è un suicidio architetturale.
 *
 *   Con la separazione, la logica condivisa (la query al DB) sta nel DAO.
 *   Ogni controller si occupa SOLO del suo formato di output.
 *
 * NOTA: In un progetto reale, la connessione al DB verrebbe ottenuta
 * tramite JNDI DataSource (configurato in context.xml di Tomcat).
 * Qui per semplicità mostriamo il pattern; l'implementazione di
 * ConnectionFactory verrà nella prossima fase.
 * ============================================================================
 */
@WebServlet("/Prodotti")
public class WebProdottoController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // In produzione: Connection conn = ConnectionFactory.getConnection();
        // Per ora usiamo un placeholder che andremo a implementare.
        Connection conn = null;

        try {
            conn = getServletContext().getAttribute("dbConnection") != null
                    ? (Connection) getServletContext().getAttribute("dbConnection")
                    : null;

            if (conn == null) {
                // Fallback per dimostrazione: errore esplicito
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Connessione al DB non disponibile");
                return;
            }

            // -----------------------------------------------------------------
            // STEP 1: Chiama il DAO — la stessa identica classe usata dall'API
            // -----------------------------------------------------------------
            ProdottoDAO dao = new ProdottoDAO(conn);
            List<Prodotto> prodotti = dao.getProdottiRadice();

            // -----------------------------------------------------------------
            // STEP 2: Metti i dati nella request come attributo.
            // Thymeleaf li leggerà nel template con ${prodotti}.
            // -----------------------------------------------------------------
            request.setAttribute("prodotti", prodotti);

            // -----------------------------------------------------------------
            // STEP 3: Forward al template Thymeleaf.
            // FORWARD, non REDIRECT. Con il forward:
            //   - L'URL nel browser resta "/Prodotti"
            //   - Gli attributi nella request sono visibili al template
            //   - Non si fa un secondo round-trip HTTP
            // Con il redirect:
            //   - Si perde tutto quello che hai messo nella request
            //   - L'URL cambia
            //   - Serve un secondo ciclo request/response
            // -----------------------------------------------------------------
            request.getRequestDispatcher("/WEB-INF/templates/prodotti.html")
                   .forward(request, response);

        } catch (SQLException e) {
            // MAI esporre i dettagli dell'eccezione SQL al client.
            // Potrebbe rivelare nomi di tabelle, colonne, versione del DB →
            // informazioni preziose per un attaccante.
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel recupero dei prodotti");
        }
    }
}
