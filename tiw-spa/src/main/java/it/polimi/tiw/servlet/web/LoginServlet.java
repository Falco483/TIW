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

    @Override
    public void init() throws ServletException {
        try {
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new UnavailableException("Connessione al DB fallita");
        }
    }

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
            // Se è cliente, ma la SPA per cliente non esiste ancora in questo modulo
            response.getWriter().write("SPA per Cliente non implementata.");
        }
    }

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
