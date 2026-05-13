package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dto.UtenteSessionDTO;
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
 * Servlet che gestisce la visualizzazione della pagina di configurazione di un prodotto.
 * Supporta sia la creazione di una nuova configurazione che la modifica di una esistente.
 */
@WebServlet("/cliente/configura")
public class ConfiguraServlet extends HttpServlet {

    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private Connection connection = null;
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    /**
     * Inizializza la servlet, stabilendo la connessione al database e configurando Thymeleaf.
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
     * Chiude la connessione al database al termine del ciclo di vita della servlet.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {}
    }

    /**
     * Gestisce le richieste GET per la configurazione.
     * Recupera l'albero del prodotto dal database e, se richiesto, carica una configurazione
     * esistente per permetterne la modifica pre-popolando le scelte dell'utente.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        // Recupera il codice del prodotto radice
        String codiceStr = request.getParameter("codice");
        if (codiceStr == null || codiceStr.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Codice prodotto mancante");
            return;
        }

        int codice;
        try {
            codice = Integer.parseInt(codiceStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Codice prodotto non valido");
            return;
        }

        Connection conn = this.connection;
        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        try {
            ProdottoDAO dao = new ProdottoDAO(conn);
            // Carica l'intero albero gerarchico del prodotto tramite il suo codice
            Prodotto albero = dao.getAlberoProdottoByCodice(codice);

            if (albero == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Prodotto non trovato");
                return;
            }

            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext ctx = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("radice", albero);

            // --- Modalità MODIFICA: parametro opzionale idConfig ---
            // Se è presente idConfig, carichiamo i dati di una configurazione salvata in precedenza
            String idConfigStr = request.getParameter("idConfig");
            if (idConfigStr != null && !idConfigStr.isEmpty()) {
                try {
                    int idConfig = Integer.parseInt(idConfigStr);

                    HttpSession session = request.getSession(false);
                    UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");

                    ConfigurazioneDAO cDao = new ConfigurazioneDAO(conn);

                    // Verifica di sicurezza: la configurazione deve esistere e appartenere all'utente corrente
                    Configurazione confEsistente = cDao.getConfigurazioneById(idConfig, utente.username());
                    if (confEsistente == null) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "Configurazione non trovata o non di proprietà");
                        return;
                    }

                    // Recupera la mappa delle SKU precedentemente scelte (prodotto -> SKU)
                    Map<Integer, Integer> mappaScelte = cDao.getScelteDettaglio(idConfig);

                    // Passa i dati al template per la pre-selezione
                    ctx.setVariable("mappaScelte", mappaScelte);
                    ctx.setVariable("idConfigInModifica", idConfig);
                    ctx.setVariable("nomeConfigurazione", confEsistente.getNome());
                } catch (NumberFormatException e) {
                    // idConfig non valido → ignora, procede come nuova configurazione
                }
            }
            
            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("configura", ctx, response.getWriter());
            
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel caricamento del prodotto");
        }
    }
}

