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
 * Controller API per la lista prodotti (JSON).
 * Chiama lo STESSO DAO da tiw-core, serializza con Jackson.
 */
@WebServlet("/api/prodotti")
public class ApiProdottoController extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Connection connection = null;

    @Override
    public void init() throws jakarta.servlet.ServletException {
        try {
            connection = it.polimi.tiw.utils.ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
        }
    }

    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {}
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Connection conn = this.connection;

        if (conn == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Connessione al DB non disponibile");
            return;
        }

        try {
            ProdottoDAO dao = new ProdottoDAO(conn);
            List<Prodotto> prodotti = dao.getProdottiRadice();
            MAPPER.writeValue(response.getOutputStream(), Map.of("data", prodotti));
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel recupero dei prodotti");
        }
    }

    private void sendError(HttpServletResponse response, int status, String messaggio)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), Map.of("errore", messaggio));
    }
}
