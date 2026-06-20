package it.polimi.tiw.servlet.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import it.polimi.tiw.dao.UtenteDAO;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.utils.ConnectionFactory;
import it.polimi.tiw.utils.UserRole;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet di Login per la SPA.
 * Non usa Thymeleaf. Redirige a login.html (in caso di GET o errore)
 * o alla home-fornitore.html in caso di successo.
 */
@WebServlet({"/login", "/logout"})
public class LoginServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;
    private Connection connection = null;

    /**
     * Inizializza la servlet ricavando la connessione al database tramite ConnectionFactory.
     *
     * @throws ServletException se il caricamento del driver o la connessione fallisce.
     */
    @Override
    public void init() throws ServletException {
        try {
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new UnavailableException("Connessione al DB fallita");
        }
    }

    /**
     * Gestisce la richiesta HTTP GET. Se invocata sul path "/logout" invalida la sessione dell'utente,
     * dopodiché effettua un reindirizzamento alla pagina statica di login.html.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errori.
     * @throws IOException in caso di errori di I/O.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        if (request.getRequestURI().endsWith("/logout")) {
            HttpSession session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
        }
        response.sendRedirect(request.getContextPath() + "/login.html");
    }

    /**
     * Gestisce la richiesta HTTP POST per eseguire l'autenticazione.
     * Legge le credenziali (username e password) inviate dal form di login, le verifica tramite il
     * DAO e, in caso di successo, crea la sessione utente generando un token CSRF univoco.
     * Infine, effettua il reindirizzamento verso la home specifica in base al ruolo dell'utente.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errori.
     * @throws IOException in caso di errori di I/O.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            return;
        }

        UtenteSessionDTO utente;
        try {
            UtenteDAO utenteDAO = new UtenteDAO(connection);
            utente = utenteDAO.checkCredentials(username, password);
        } catch (SQLException e) {
            response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            return;
        }

        if (utente == null) {
            response.sendRedirect(request.getContextPath() + "/login.html?error=1");
            return;
        }

        // Creazione sessione
        HttpSession session = request.getSession();
        session.setAttribute(UtenteSessionDTO.SESSION_KEY, utente);
        session.setAttribute("csrfToken", UUID.randomUUID().toString());

        // Redirect alla SPA tramite la servlet che inietta il CSRF token
        if (utente.ruolo() == UserRole.FORNITORE) {
            response.sendRedirect(request.getContextPath() + "/home-fornitore");
        } else {
            response.sendRedirect(request.getContextPath() + "/home-cliente");
        }
    }

    /**
     * Distrugge la servlet chiudendo la connessione al database.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            // Ignora
        }
    }
}
