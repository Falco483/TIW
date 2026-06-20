package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.dto.VoceConfigurazioneDTO;
import it.polimi.tiw.model.Configurazione;
import it.polimi.tiw.model.Prodotto;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Servlet che gestisce la visualizzazione in modalità di sola lettura del dettaglio
 * di una configurazione salvata dal cliente.
 * Mostra l'intero albero dei prodotti con le relative SKU scelte ed i prezzi storici "congelati".
 */
@WebServlet("/cliente/dettaglio")
public class DettaglioConfigurazioneServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private Connection connection = null;
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    /**
     * Inizializza la servlet ricavando la connessione al database e configurando Thymeleaf.
     *
     * @throws ServletException se l'inizializzazione fallisce.
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
     * Gestisce la richiesta HTTP GET. Recupera dal database la configurazione, l'albero
     * del prodotto e le voci di dettaglio congelate per poi passarli a Thymeleaf.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errore.
     * @throws IOException in caso di errore di I/O.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String idConfigStr = request.getParameter("idConfig");
        if (idConfigStr == null || idConfigStr.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametro idConfig mancante");
            return;
        }

        int idConfig;
        try {
            idConfig = Integer.parseInt(idConfigStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametro idConfig non valido");
            return;
        }

        HttpSession session = request.getSession(false);
        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");

        Connection conn = this.connection;
        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        try {
            ConfigurazioneDAO cDao = new ConfigurazioneDAO(conn);
            ProdottoDAO pDao = new ProdottoDAO(conn);

            Configurazione configurazione = cDao.getConfigurazioneById(idConfig, utente.username());
            if (configurazione == null) {
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Configurazione non trovata o non di proprietà");
                return;
            }

            Prodotto radice = pDao.getAlberoProdotto(configurazione.getProdottoRadiceId());
            if (radice == null) {
                response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Prodotto radice non trovato");
                return;
            }

            Map<Integer, VoceConfigurazioneDTO> mappaVoci = cDao.getVociDettaglio(idConfig);

            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext ctx = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("configurazione", configurazione);
            ctx.setVariable("radice", radice);
            ctx.setVariable("mappaVoci", mappaVoci);

            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("cliente/dettaglio", ctx, response.getWriter());

        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel recupero del dettaglio");
        }
    }
}
