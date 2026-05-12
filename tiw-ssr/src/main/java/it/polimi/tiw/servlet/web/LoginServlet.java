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

/**
 * Servlet che gestisce l'autenticazione degli utenti (Login).
 * Fornisce il form di login (GET) e processa le credenziali inviate (POST).
 * In caso di successo, crea la sessione utente e genera un token CSRF.
 */
@WebServlet("/login")
public class LoginServlet extends HttpServlet {

    private Connection connection = null;
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    /**
     * Inizializza la servlet stabilendo la connessione al database e configurando Thymeleaf.
     */
    @Override
    public void init() throws ServletException {
        try {
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (Exception e) {
            e.printStackTrace();
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

    /**
     * Mostra la pagina di login (form vuoto).
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        mostraLogin(request, response, null);
    }

    /**
     * Gestisce l'invio delle credenziali di login.
     * Valida i dati, interroga il database tramite UtenteDAO e, se corretti,
     * inizializza la sessione utente e reindirizza alla home corretta (Cliente o Fornitore).
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

        // 1. Leggere i parametri della richiesta
        String username = request.getParameter("username");
        String password = request.getParameter("password");

        // 2. Validazione base
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
        // Generazione del token CSRF per la sicurezza delle operazioni POST successive
        session.setAttribute("csrfToken", java.util.UUID.randomUUID().toString());

        String contextPath = request.getContextPath();

        // Redirect basato sul ruolo dell'utente
        if (utente.ruolo() == UserRole.CLIENTE) {
            response.sendRedirect(contextPath + "/cliente/home");
            return;
        }
        response.sendRedirect(contextPath + "/fornitore/home");
    }

    /**
     * Metodo helper per renderizzare la pagina di login, opzionalmente con un messaggio di errore.
     */
    private void mostraLogin(HttpServletRequest request, HttpServletResponse response,
                              String errore) throws IOException {
        IWebExchange webExchange = webApp.buildExchange(request, response);
        WebContext ctx = new WebContext(webExchange, request.getLocale());
        if (errore != null) {
            ctx.setVariable("errore", errore);
            ctx.setVariable("usernameInserito", request.getParameter("username"));
        }
        response.setContentType("text/html;charset=UTF-8");
        templateEngine.process("login", ctx, response.getWriter());
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
            // log
        }
    }
}