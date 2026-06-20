package it.polimi.tiw.servlet.web;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Servlet che gestisce il logout degli utenti invalidando la sessione attiva.
 */
@WebServlet("/logout")
public class LogoutServlet extends HttpServlet {

    /**
     * Gestisce la richiesta HTTP POST per il logout. Invalida la sessione se presente
     * e reindirizza alla pagina di login.
     *
     * @param req la servlet request.
     * @param resp la servlet response.
     * @throws ServletException in caso di errore.
     * @throws IOException in caso di errore di I/O.
     */
    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {
        HttpSession session = req.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        resp.sendRedirect(req.getContextPath() + "/login");
    }
}
