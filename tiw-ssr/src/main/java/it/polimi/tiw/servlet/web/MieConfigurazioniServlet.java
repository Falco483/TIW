package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.model.Configurazione;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Servlet che mostra la lista delle configurazioni salvate dal cliente.
 * Consente di visualizzare, clonare, modificare o eliminare le proprie configurazioni.
 */
@WebServlet("/cliente/configurazioni")
public class MieConfigurazioniServlet extends HttpServlet {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    /**
     * Inizializza la servlet stabilendo la connessione al database e configurando Thymeleaf.
     */
    @Override
    public void init() throws ServletException {
        try {
            connection = it.polimi.tiw.utils.ConnectionFactory.getConnection(getServletContext());

            webApp = JakartaServletWebApplication.buildApplication(getServletContext());
            WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(webApp);
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setPrefix("/WEB-INF/templates/");
            resolver.setSuffix(".html");
            templateEngine = new TemplateEngine();
            templateEngine.setTemplateResolver(resolver);
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Inizializzazione fallita");
        }
    }

    /**
     * Chiude la connessione al database.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {}
    }

    /**
     * Gestisce la richiesta HTTP GET. Recupera dal database l'elenco delle configurazioni
     * salvate dall'utente corrente e ne effettua il rendering con Thymeleaf.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errore della servlet.
     * @throws IOException in caso di errori di I/O.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");

        Connection conn = this.connection;
        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        try {
            ConfigurazioneDAO cDao = new ConfigurazioneDAO(conn);
            // Recupera la lista delle configurazioni dell'utente (ordinata per data_modifica DESC)
            List<Configurazione> configurazioni = cDao.getConfigurazioniByUtente(utente.username());

            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext ctx = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("configurazioni", configurazioni);

            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("cliente/mieConfigurazioni", ctx, response.getWriter());

        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel recupero delle configurazioni");
        }
    }
}
