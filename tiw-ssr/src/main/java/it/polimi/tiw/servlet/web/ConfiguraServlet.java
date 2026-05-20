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
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Servlet che gestisce la visualizzazione della pagina di configurazione di un
 * prodotto.
 * Supporta sia la creazione di una nuova configurazione che la modifica di una
 * esistente.
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
     * Inizializza la servlet, stabilendo la connessione al database e configurando
     * Thymeleaf.
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
        } catch (SQLException e) {
        }
    }

    /**
     * Gestisce le richieste GET per la configurazione.
     * Recupera l'albero del prodotto dal database e, se richiesto, carica una
     * configurazione
     * esistente per permetterne la modifica pre-popolando le scelte dell'utente.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

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

        // Leggi i nodi già espansi dai parametri GET (?aperto=5&aperto=7&...)
        String[] apertoStr = request.getParameterValues("aperto");
        Set<Integer> aperti = new HashSet<>();
        if (apertoStr != null) {
            for (String s : apertoStr) {
                try {
                    aperti.add(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "URL mal formato");
                    return;
                }
            }
        }

        HttpSession session = request.getSession(false);

        try {
            ProdottoDAO dao = new ProdottoDAO(connection);
            Prodotto albero = dao.getAlberoProdottoByCodice(codice);

            if (albero == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Prodotto non trovato");
                return;
            }

            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext ctx = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("radice", albero);
            ctx.setVariable("apertoSet", aperti);

            String idConfigStr = request.getParameter("idConfig");
            if (idConfigStr != null && !idConfigStr.isEmpty()) {
                try {
                    int idConfig = Integer.parseInt(idConfigStr);

                    UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");
                    ConfigurazioneDAO cDao = new ConfigurazioneDAO(connection);

                    Configurazione confEsistente = cDao.getConfigurazioneById(idConfig, utente.username());
                    if (confEsistente == null) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "Configurazione non trovata o non di proprietà");
                        return;
                    }

                    // In modalità modifica la sessione è persistente (non flash):
                    // alla prima visita inizializziamo da DB e salviamo in sessione;
                    // nelle GET successive (dopo espansioni) la sessione ha già le scelte
                    // aggiornate
                    Map<Integer, Integer> mappaScelte = (Map<Integer, Integer>) session
                            .getAttribute("configura.mappaScelte");
                    if (mappaScelte == null) {
                        mappaScelte = cDao.getScelteDettaglio(idConfig);
                        session.setAttribute("configura.mappaScelte", mappaScelte);
                    }

                    // errore e nomeInserito: flash attributes salvati da ritornaAllaFormConErrore
                    String errore = (String) session.getAttribute("configura.errore");
                    session.removeAttribute("configura.errore");
                    String nomeInserito = (String) session.getAttribute("configura.nomeInserito");
                    session.removeAttribute("configura.nomeInserito");

                    ctx.setVariable("mappaScelte", mappaScelte);
                    ctx.setVariable("idConfig", idConfig);
                    ctx.setVariable("nomeConfigurazione", confEsistente.getNome());
                    ctx.setVariable("errore", errore);
                    ctx.setVariable("nomeInserito", nomeInserito);
                    response.setContentType("text/html;charset=UTF-8");
                    templateEngine.process("configura", ctx, response.getWriter());
                    return;
                } catch (NumberFormatException e) {
                    // idConfig non valido → ignora, procede come nuova configurazione
                }
            }

            // Modalità nuova configurazione: mappaScelte è persistente in sessione (si pulisce
            // solo al salvataggio con successo), errore e nomeInserito sono flash attributes
            Map<Integer, Integer> mappaScelte = null;
            String errore = null;
            String nomeInserito = null;
            if (session != null) {
                mappaScelte = (Map<Integer, Integer>) session.getAttribute("configura.mappaScelte");
                errore = (String) session.getAttribute("configura.errore");
                session.removeAttribute("configura.errore");
                nomeInserito = (String) session.getAttribute("configura.nomeInserito");
                session.removeAttribute("configura.nomeInserito");
            }
            ctx.setVariable("mappaScelte", mappaScelte);
            ctx.setVariable("errore", errore);
            ctx.setVariable("nomeInserito", nomeInserito);
            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("configura", ctx, response.getWriter());

        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel caricamento del prodotto");
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String codiceRadiceStr = request.getParameter("codiceRadice");
        String idConfigStr = request.getParameter("idConfig");
        String espandiIdStr = request.getParameter("espandi");

        // codiceRadice è obbligatorio
        if (codiceRadiceStr == null || codiceRadiceStr.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Codice radice mancante");
            return;
        }

        int codiceRadice;
        try {
            codiceRadice = Integer.parseInt(codiceRadiceStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Codice radice non valido");
            return;
        }

        // Raccoglie le selezioni SKU correnti dai parametri "sku_<idProdotto>=<idSku>"
        Map<Integer, Integer> mappaScelte = new HashMap<>();
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String nome = params.nextElement();
            if (nome.startsWith("sku_")) {
                try {
                    int idProdotto = Integer.parseInt(nome.substring(4));
                    int idSku = Integer.parseInt(request.getParameter(nome));
                    mappaScelte.put(idProdotto, idSku);
                } catch (NumberFormatException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametri SKU non validi");
                    return;
                }
            }
        }

        // Raccoglie i nodi già espansi dai hidden fields "aperto"
        Set<Integer> apertoSet = new HashSet<>();
        String[] apertoParams = request.getParameterValues("aperto");
        if (apertoParams != null) {
            for (String s : apertoParams) {
                try {
                    apertoSet.add(Integer.parseInt(s));
                } catch (NumberFormatException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametri aperto non validi");
                    return;
                }
            }
        }

        if (espandiIdStr != null) {
            // Branch espansione: aggiunge il nodo richiesto all'insieme degli aperti,
            // salva le scelte in sessione e reindirizza alla GET con lo stato aggiornato
            int espandiId;
            try {
                espandiId = Integer.parseInt(espandiIdStr);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID espandi non valido");
                return;
            }

            apertoSet.add(espandiId);

            HttpSession session = request.getSession();
            // Merge: parte dalla mappa in sessione (contiene scelte di nodi non visibili)
            // e sovrascrive con i valori del form (scelte aggiornate dall'utente)
            Map<Integer, Integer> mappaBase = (Map<Integer, Integer>) session.getAttribute("configura.mappaScelte");
            if (mappaBase != null) {
                mappaBase.putAll(mappaScelte);
                mappaScelte = mappaBase;
            }
            session.setAttribute("configura.mappaScelte", mappaScelte);

            StringBuilder url = new StringBuilder(request.getContextPath() + "/cliente/configura");
            url.append("?codice=").append(codiceRadice);
            for (int id : apertoSet) {
                url.append("&aperto=").append(id);
            }
            if (idConfigStr != null && !idConfigStr.isEmpty()) {
                url.append("&idConfig=").append(idConfigStr);
            }
            response.sendRedirect(url.toString());

        } else {
            // Branch salvataggio: delega a SalvaConfigurazioneServlet tramite forward
            getServletContext().getRequestDispatcher("/cliente/salva").forward(request, response);
        }
    }
}
