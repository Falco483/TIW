package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.model.Configurazione;
import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.ProdottoComposto;

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
     *
     * Le scelte SKU dell'utente viaggiano nel form come hidden fields (stateless):
     * non vengono mai scritte in sessione da questo metodo.
     * La sessione viene letta solo per i flash attributes (errore, nomeInserito,
     * mappaScelte) lasciati da SalvaConfigurazioneServlet in caso di errore di
     * validazione, e rimossi immediatamente dopo la lettura.
     *
     * Priorità per costruire mappaScelte:
     *  1. Parametri GET "sku_*"  → redirect post-espansione (contengono già tutto)
     *  2. Flash "configura.mappaScelte" in sessione → redirect post-errore salvataggio
     *  3. DB (solo in modalità modifica, prima visita senza scelte nell'URL)
     *  4. null → nuova configurazione senza scelte pregresse
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

        // 1. Prova a leggere le scelte dai parametri GET "sku_*" (redirect post-espansione)
        Map<Integer, Integer> mappaScelte = new HashMap<>();
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String nome = params.nextElement();
            if (nome.startsWith("sku_")) {
                try {
                    int idProdotto = Integer.parseInt(nome.substring(4));
                    int idSku     = Integer.parseInt(request.getParameter(nome));
                    mappaScelte.put(idProdotto, idSku);
                } catch (NumberFormatException e) {
                    response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametri SKU non validi nell'URL");
                    return;
                }
            }
        }

        // Flash attributes lasciati da SalvaConfigurazioneServlet in caso di errore
        HttpSession session = request.getSession(false);
        String errore      = null;
        String nomeInserito = null;
        if (session != null) {
            errore       = (String) session.getAttribute("configura.errore");
            nomeInserito = (String) session.getAttribute("configura.nomeInserito");
            session.removeAttribute("configura.errore");
            session.removeAttribute("configura.nomeInserito");

            // 2. Flash mappaScelte → redirect post-errore (sovrascrive i parametri GET,
            //    che in quel caso non sono presenti)
            if (mappaScelte.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<Integer, Integer> flash =
                    (Map<Integer, Integer>) session.getAttribute("configura.mappaScelte");
                if (flash != null) {
                    mappaScelte = flash;
                }
            }
            session.removeAttribute("configura.mappaScelte");
        }

        // Se il flash non ha già impostato nomeInserito (caso errore), prendi dal
        // parametro GET — è il nome che l'utente aveva scritto prima di cliccare Espandi
        if (nomeInserito == null) {
            nomeInserito = request.getParameter("nomeConfigurazione");
        }

        try {
            ProdottoDAO dao   = new ProdottoDAO(connection);
            Prodotto    albero = dao.getAlberoProdottoByCodice(codice);

            if (albero == null) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, "Prodotto non trovato");
                return;
            }

            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext   ctx         = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("radice",    albero);
            ctx.setVariable("apertoSet", aperti);
            ctx.setVariable("errore",    errore);
            ctx.setVariable("nomeInserito", nomeInserito);

            String idConfigStr = request.getParameter("idConfig");
            if (idConfigStr != null && !idConfigStr.isEmpty()) {
                try {
                    int idConfig = Integer.parseInt(idConfigStr);

                    UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");
                    ConfigurazioneDAO cDao  = new ConfigurazioneDAO(connection);

                    Configurazione confEsistente = cDao.getConfigurazioneById(idConfig, utente.username());
                    if (confEsistente == null) {
                        response.sendError(HttpServletResponse.SC_FORBIDDEN,
                                "Configurazione non trovata o non di proprietà");
                        return;
                    }

                    // 3. Prima visita in modalità modifica: nessuna scelta nell'URL né in flash
                    //    → carica dal DB
                    if (mappaScelte.isEmpty()) {
                        mappaScelte = cDao.getScelteDettaglio(idConfig);
                    }

                    ctx.setVariable("idConfig",           idConfig);
                    ctx.setVariable("nomeConfigurazione", confEsistente.getNome());
                } catch (NumberFormatException e) {
                    // idConfig non valido → ignora, tratta come nuova configurazione
                }
            }

            // Calcola quali prodotti semplici sono attualmente visibili nel form
            // (hanno un <select> renderizzato). Serve al template per sapere
            // quali scelte emettere come hidden field invece.
            Set<Integer> prodottiVisibili = calcolaProdottiVisibili(albero, aperti, true);

            ctx.setVariable("mappaScelte",       mappaScelte.isEmpty() ? null : mappaScelte);
            ctx.setVariable("prodottiVisibili",  prodottiVisibili);
            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("configura", ctx, response.getWriter());

        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel caricamento del prodotto");
        }
    }

    /**
     * Calcola ricorsivamente l'insieme degli id dei prodotti SEMPLICI attualmente
     * visibili nell'albero (quelli per cui il template renderizza un {@code <select>}).
     * Un prodotto semplice è visibile se tutti i suoi antenati composti sono espansi
     * (ovvero sono la radice oppure presenti in {@code aperti}).
     */
    private Set<Integer> calcolaProdottiVisibili(Prodotto nodo, Set<Integer> aperti, boolean isRadice) {
        Set<Integer> visibili = new HashSet<>();
        if ("SEMPLICE".equals(nodo.getTipo())) {
            visibili.add(nodo.getId());
        } else if ("COMPOSTO".equals(nodo.getTipo()) && nodo instanceof ProdottoComposto composto) {
            if (isRadice || aperti.contains(nodo.getCodice())) {
                for (Prodotto figlio : composto.getFigli()) {
                    visibili.addAll(calcolaProdottiVisibili(figlio, aperti, false));
                }
            }
        }
        return visibili;
    }

    /**
     * Gestisce le POST della pagina di configurazione, in due rami:
     * - espansione di un nodo composto: aggiorna l'insieme dei nodi aperti e
     *   reindirizza alla GET portando le scelte correnti come parametri "sku_*"
     *   nell'URL (approccio stateless, senza scrittura in sessione);
     * - salvataggio: inoltra la richiesta a SalvaConfigurazioneServlet.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errore della servlet.
     * @throws IOException in caso di errori di I/O.
     */
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
            // Branch espansione: aggiunge il nodo richiesto all'insieme degli aperti
            // e reindirizza alla GET portando le scelte correnti come parametri "sku_*"
            // nell'URL (nessuna scrittura in sessione).
            int espandiId;
            try {
                espandiId = Integer.parseInt(espandiIdStr);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID espandi non valido");
                return;
            }

            apertoSet.add(espandiId);

            StringBuilder url = new StringBuilder(request.getContextPath() + "/cliente/configura");
            url.append("?codice=").append(codiceRadice);
            for (int id : apertoSet) {
                url.append("&aperto=").append(id);
            }
            if (idConfigStr != null && !idConfigStr.isEmpty()) {
                url.append("&idConfig=").append(idConfigStr);
            }
            // Le scelte SKU viaggiano come query params: il doGet le rileverà con priorità 1
            for (Map.Entry<Integer, Integer> entry : mappaScelte.entrySet()) {
                url.append("&sku_").append(entry.getKey()).append("=").append(entry.getValue());
            }
            // Il nome configurazione inserito sopravvive al redirect
            String nomeConfigurazione = request.getParameter("nomeConfigurazione");
            if (nomeConfigurazione != null && !nomeConfigurazione.isEmpty()) {
                url.append("&nomeConfigurazione=").append(
                    java.net.URLEncoder.encode(nomeConfigurazione, java.nio.charset.StandardCharsets.UTF_8));
            }
            response.sendRedirect(url.toString());

        } else {
            // Branch salvataggio: delega a SalvaConfigurazioneServlet tramite forward
            getServletContext().getRequestDispatcher("/cliente/salva").forward(request, response);
        }
    }
}
