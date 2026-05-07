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
 * Controller SSR per la lista prodotti (Thymeleaf).
 * Chiama il DAO da tiw-core, fa forward al template.
 */
@WebServlet("/Prodotti")
public class WebProdottoController extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Connection conn = getServletContext().getAttribute("dbConnection") != null
                ? (Connection) getServletContext().getAttribute("dbConnection")
                : null;

        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Connessione al DB non disponibile");
            return;
        }

        try {
            ProdottoDAO dao = new ProdottoDAO(conn);
            List<Prodotto> prodotti = dao.getProdottiRadice();
            request.setAttribute("prodotti", prodotti);
            request.getRequestDispatcher("/WEB-INF/templates/prodotti.html")
                   .forward(request, response);
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel recupero dei prodotti");
        }
    }
}
