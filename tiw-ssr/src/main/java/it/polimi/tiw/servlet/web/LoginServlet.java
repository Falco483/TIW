package it.polimi.tiw.servlet.web;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

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

@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private Connection connection = null;
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    @Override
    public void init() throws ServletException {
        try {
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new UnavailableException("Connessione al DB fallita");
        }

        webApp = JakartaServletWebApplication.buildApplication(getServletContext());

        WebApplicationTemplateResolver resolver =
            new WebApplicationTemplateResolver(webApp);

        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        mostraLogin(request, response, null);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        // 1. Leggere i parametri della richiesta
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        // 2. Validazione base (mai fidarsi del client)
        if (username == null || username.isBlank()
            || password == null || password.isBlank()) {
            mostraLogin(request, response, "Inserire username e password.");
            return;
        }

        // 3. Verifica credenziali nel DB tramite DAO
        UtenteSessionDTO utente;
        try {
            UtenteDAO utenteDAO = new UtenteDAO(connection);
            utente = utenteDAO.checkCredentials(username, password);
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore DB durante il login.");
            return;
        }

        // 4a. Credenziali errate → torna al form con errore
        if (utente == null) {
            mostraLogin(request, response, "Credenziali non valide.");
            return;
        }

        // 4b. Credenziali corrette → crea sessione e redirect alla home
        HttpSession session = request.getSession();
        session.setAttribute(UtenteSessionDTO.SESSION_KEY, utente);
        session.setAttribute("csrfToken", java.util.UUID.randomUUID().toString());

        String contextPath = request.getContextPath();

        if (utente.ruolo() == UserRole.CLIENTE) {
            response.sendRedirect(contextPath + "/cliente/home");
            return;
        }
        response.sendRedirect(contextPath + "/fornitore/home");
    }

    private void mostraLogin(HttpServletRequest request, HttpServletResponse response,
                             String errore) throws IOException {
        IWebExchange webExchange = webApp.buildExchange(request, response);
        WebContext ctx = new WebContext(webExchange, request.getLocale());
        if (errore != null) {
            ctx.setVariable("errore", errore);
        }
        response.setContentType("text/html;charset=UTF-8");
        templateEngine.process("login", ctx, response.getWriter());
    }

    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
            // log
        }
    }
}