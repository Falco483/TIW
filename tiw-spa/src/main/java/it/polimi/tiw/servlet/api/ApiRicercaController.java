package it.polimi.tiw.servlet.api;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.model.ElementoCatalogo;
import it.polimi.tiw.utils.ConnectionFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller API per la ricerca unificata su prodotti e SKU.
 */
@WebServlet("/api/ricerca")
public class ApiRicercaController extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Connection connection = null;

    /**
     * Inizializza la servlet e ottiene la connessione al database tramite la ConnectionFactory.
     * @throws ServletException se la connessione al database fallisce.
     */
    @Override
    public void init() throws ServletException {
        try {
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
        }
    }

    /**
     * Termina il ciclo di vita della servlet chiudendo in modo sicuro la connessione al database.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException ignored) {}
    }

    /**
     * Gestisce la ricerca unificata tramite metodo GET.
     * Cerca la stringa 'q' in parallelo sulle tabelle dei Prodotti e delle SKU utilizzando i rispettivi DAO,
     * quindi combina i risultati in un'unica lista polimorfica di ElementoCatalogo.
     * @param request La richiesta HTTP contenente il parametro di ricerca 'q'.
     * @param response La risposta HTTP contenente il payload JSON con i risultati unificati.
     * @throws IOException Se si verifica un errore durante la serializzazione JSON.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String q = request.getParameter("q");
        if (q == null || q.isBlank()) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            MAPPER.writeValue(response.getOutputStream(), Map.of(
                    "success", false,
                    "error", "Parametro di ricerca 'q' mancante o vuoto"
            ));
            return;
        }

        try {
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            SKUDAO skuDAO = new SKUDAO(connection);

            List<ElementoCatalogo> risultati = new ArrayList<>();
            risultati.addAll(prodottoDAO.search(q));
            risultati.addAll(skuDAO.search(q));

            MAPPER.writeValue(response.getOutputStream(), Map.of("data", risultati));
        } catch (SQLException e) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            MAPPER.writeValue(response.getOutputStream(), Map.of(
                    "success", false,
                    "error", "Errore nella ricerca: " + e.getMessage()
            ));
        }
    }
}
