package it.polimi.tiw.servlet.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpSession;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.ProdottoSemplice;
import it.polimi.tiw.model.SKU;
import it.polimi.tiw.utils.ConnectionFactory;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/fornitore/home")
public class HomeFornitoreServlet extends HttpServlet {

    private static final String SESSION_ERRORI         = "home.errori";
    private static final String SESSION_VALORI_FORM    = "home.valoriForm";
    private static final String SESSION_RISULTATO      = "home.risultato";
    private static final String SESSION_TIPO_RISULTATO = "home.tipoRisultato";

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
        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(webApp);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");
        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {
        }
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        List<String> errori = null;
        Map<String, String> valoriForm = null;
        Object risultato = null;
        String tipoRisultato = null;

        if (session != null) {
            errori        = (List<String>) session.getAttribute(SESSION_ERRORI);
            valoriForm    = (Map<String, String>) session.getAttribute(SESSION_VALORI_FORM);
            risultato     = session.getAttribute(SESSION_RISULTATO);
            tipoRisultato = (String) session.getAttribute(SESSION_TIPO_RISULTATO);

            session.removeAttribute(SESSION_ERRORI);
            session.removeAttribute(SESSION_VALORI_FORM);
            session.removeAttribute(SESSION_RISULTATO);
            session.removeAttribute(SESSION_TIPO_RISULTATO);
        }

        renderHome(request, response, errori, valoriForm, risultato, tipoRisultato);
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String tipoForm = request.getParameter("tipoForm");
        if (tipoForm == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametro tipoForm mancante");
            return;
        }
        switch (tipoForm) {
            case "sku"      -> handleCreaSku(request, response);
            case "semplice" -> handleCreaSemplice(request, response);
            case "composto" -> handleCreaComposto(request, response);
            default         -> response.sendError(HttpServletResponse.SC_BAD_REQUEST, "tipoForm non valido");
        }
    }

    // -------------------------------------------------------------------------
    // Helper PRG
    // -------------------------------------------------------------------------

    private void redirectConErrore(HttpServletRequest req, HttpServletResponse res,
                                    List<String> errori, Map<String, String> valoriForm)
            throws IOException {
        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_ERRORI, errori);
        session.setAttribute(SESSION_VALORI_FORM, valoriForm);
        res.sendRedirect(req.getContextPath() + "/fornitore/home");
    }

    private void redirectConSuccesso(HttpServletRequest req, HttpServletResponse res,
                                      Object risultato, String tipoRisultato)
            throws IOException {
        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_RISULTATO, risultato);
        session.setAttribute(SESSION_TIPO_RISULTATO, tipoRisultato);
        res.sendRedirect(req.getContextPath() + "/fornitore/home");
    }

    // -------------------------------------------------------------------------
    // Handlers POST
    // -------------------------------------------------------------------------

    private void handleCreaSku(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String nomeRaw           = request.getParameter("nomeSku");
        String codiceRaw         = request.getParameter("codiceSku");
        String fotografiaRaw     = request.getParameter("fotografiaSku");
        String descrizioneTecRaw = request.getParameter("descrizioneTecnicaSku");
        String prezzoRaw         = request.getParameter("prezzoSku");

        Map<String, String> valoriForm = new HashMap<>();
        valoriForm.put("nomeSku", nomeRaw);
        valoriForm.put("codiceSku", codiceRaw);
        valoriForm.put("fotografiaSku", fotografiaRaw);
        valoriForm.put("descrizioneTecnicaSku", descrizioneTecRaw);
        valoriForm.put("prezzoSku", prezzoRaw);

        List<String> errori = new ArrayList<>();

        if (isBlank(nomeRaw))           errori.add("Il nome della SKU è obbligatorio.");
        if (isBlank(codiceRaw))         errori.add("Il codice della SKU è obbligatorio.");
        if (isBlank(fotografiaRaw))     errori.add("La fotografia della SKU è obbligatoria.");
        if (isBlank(descrizioneTecRaw)) errori.add("La descrizione tecnica della SKU è obbligatoria.");
        if (isBlank(prezzoRaw))         errori.add("Il prezzo della SKU è obbligatorio.");

        int codice = 0;
        if (!isBlank(codiceRaw)) {
            try {
                codice = Integer.parseInt(codiceRaw.trim());
            } catch (NumberFormatException e) {
                errori.add("Il codice della SKU deve essere un numero intero.");
            }
        }

        BigDecimal prezzo = null;
        if (!isBlank(prezzoRaw)) {
            try {
                prezzo = new BigDecimal(prezzoRaw.trim());
                if (prezzo.compareTo(BigDecimal.ZERO) < 0)
                    errori.add("Il prezzo della SKU non può essere negativo.");
            } catch (NumberFormatException e) {
                errori.add("Il prezzo della SKU non è un numero valido.");
            }
        }

        if (!errori.isEmpty()) {
            redirectConErrore(request, response, errori, valoriForm);
            return;
        }

        try {
            SKUDAO skuDAO = new SKUDAO(connection);
            int id = skuDAO.insert(codice, nomeRaw.trim(), fotografiaRaw.trim(),
                                   descrizioneTecRaw.trim(), prezzo);
            SKU skuCreata = skuDAO.findById(id);
            redirectConSuccesso(request, response, skuCreata, "sku");
        } catch (SQLException e) {
            redirectConErrore(request, response,
                List.of("Errore nel salvataggio della SKU: " + e.getMessage()),
                valoriForm);
        }
    }

    private void handleCreaSemplice(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String codiceRaw = request.getParameter("codiceSemplice");
        String nomeRaw   = request.getParameter("nomeSemplice");
        String[] idSkuSelezionateRaw = request.getParameterValues("idSkuSelezionate");

        Map<String, String> valoriForm = new HashMap<>();
        valoriForm.put("codiceSemplice", codiceRaw);
        valoriForm.put("nomeSemplice", nomeRaw);

        List<String> errori = new ArrayList<>();
        if (isBlank(codiceRaw)) errori.add("Il codice del prodotto semplice è obbligatorio.");
        if (isBlank(nomeRaw))   errori.add("Il nome del prodotto semplice è obbligatorio.");
        if (idSkuSelezionateRaw == null || idSkuSelezionateRaw.length == 0)
            errori.add("Selezionare almeno una SKU per il prodotto semplice.");

        List<Integer> idSkuList = new ArrayList<>();
        if (idSkuSelezionateRaw != null) {
            for (String raw : idSkuSelezionateRaw) {
                try {
                    idSkuList.add(Integer.parseInt(raw));
                } catch (NumberFormatException e) {
                    errori.add("Identificatore SKU non valido: " + raw);
                }
            }
        }

        if (!errori.isEmpty()) {
            redirectConErrore(request, response, errori, valoriForm);
            return;
        }

        try {
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            int id = prodottoDAO.insertSemplice(codiceRaw.trim(), nomeRaw.trim());
            for (int idSku : idSkuList) {
                prodottoDAO.addSku(id, idSku);
            }
            ProdottoSemplice creato = (ProdottoSemplice) prodottoDAO.getAlberoProdotto(id);
            redirectConSuccesso(request, response, creato, "semplice");
        } catch (SQLException e) {
            redirectConErrore(request, response,
                List.of("Errore nel salvataggio del prodotto semplice: " + e.getMessage()),
                valoriForm);
        }
    }

    private void handleCreaComposto(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String codiceRaw     = request.getParameter("codiceComposto");
        String nomeRaw       = request.getParameter("nomeComposto");
        String descrizioneRaw = request.getParameter("descrizioneComposto");
        String prezzoMinRaw  = request.getParameter("prezzoMinComposto");
        String prezzoMaxRaw  = request.getParameter("prezzoMaxComposto");
        String[] idFigliRaw  = request.getParameterValues("idFigli");

        Map<String, String> valoriForm = new HashMap<>();
        valoriForm.put("codiceComposto", codiceRaw);
        valoriForm.put("nomeComposto", nomeRaw);
        valoriForm.put("descrizioneComposto", descrizioneRaw);
        valoriForm.put("prezzoMinComposto", prezzoMinRaw);
        valoriForm.put("prezzoMaxComposto", prezzoMaxRaw);

        List<String> errori = new ArrayList<>();
        if (isBlank(codiceRaw))      errori.add("Il codice del prodotto composto è obbligatorio.");
        if (isBlank(nomeRaw))        errori.add("Il nome del prodotto composto è obbligatorio.");
        if (isBlank(descrizioneRaw)) errori.add("La descrizione del prodotto composto è obbligatoria.");
        if (isBlank(prezzoMinRaw))   errori.add("Il prezzo minimo è obbligatorio.");
        if (isBlank(prezzoMaxRaw))   errori.add("Il prezzo massimo è obbligatorio.");
        if (idFigliRaw == null || idFigliRaw.length == 0)
            errori.add("Selezionare almeno un sottoprodotto per il prodotto composto.");

        BigDecimal prezzoMin = null;
        BigDecimal prezzoMax = null;
        if (!isBlank(prezzoMinRaw)) {
            try {
                prezzoMin = new BigDecimal(prezzoMinRaw.trim());
                if (prezzoMin.compareTo(BigDecimal.ZERO) < 0)
                    errori.add("Il prezzo minimo non può essere negativo.");
            } catch (NumberFormatException e) {
                errori.add("Il prezzo minimo non è un numero valido.");
            }
        }
        if (!isBlank(prezzoMaxRaw)) {
            try {
                prezzoMax = new BigDecimal(prezzoMaxRaw.trim());
                if (prezzoMax.compareTo(BigDecimal.ZERO) < 0)
                    errori.add("Il prezzo massimo non può essere negativo.");
            } catch (NumberFormatException e) {
                errori.add("Il prezzo massimo non è un numero valido.");
            }
        }
        if (prezzoMin != null && prezzoMax != null && prezzoMin.compareTo(prezzoMax) > 0)
            errori.add("Il prezzo minimo non può essere maggiore del prezzo massimo.");

        List<Integer> idFigliList = new ArrayList<>();
        if (idFigliRaw != null) {
            for (String raw : idFigliRaw) {
                try {
                    idFigliList.add(Integer.parseInt(raw));
                } catch (NumberFormatException e) {
                    errori.add("Identificatore sottoprodotto non valido: " + raw);
                }
            }
        }

        if (!errori.isEmpty()) {
            redirectConErrore(request, response, errori, valoriForm);
            return;
        }

        boolean autoCommitOriginale = true;
        try {
            autoCommitOriginale = connection.getAutoCommit();
            connection.setAutoCommit(false);

            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            int id = prodottoDAO.insertComposto(codiceRaw.trim(), nomeRaw.trim(),
                                                descrizioneRaw.trim(), prezzoMin, prezzoMax);

            for (int idFiglio : idFigliList) {
                int livello = prodottoDAO.calcolaLivello(idFiglio);
                if (livello >= 4) {
                    connection.rollback();
                    redirectConErrore(request, response,
                        List.of("Il sottoprodotto selezionato supera la profondità massima di 4 livelli."),
                        valoriForm);
                    return;
                }
                prodottoDAO.addFiglio(id, idFiglio);
            }

            connection.commit();
            Prodotto creato = prodottoDAO.getAlberoProdotto(id);
            redirectConSuccesso(request, response, creato, "composto");
        } catch (IllegalStateException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            redirectConErrore(request, response,
                List.of("Un sottoprodotto appartiene già a un altro prodotto padre."),
                valoriForm);
        } catch (SQLException e) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            redirectConErrore(request, response,
                List.of("Errore nel salvataggio del prodotto composto: " + e.getMessage()),
                valoriForm);
        } finally {
            try { connection.setAutoCommit(autoCommitOriginale); } catch (SQLException ignored) {}
        }
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    private void renderHome(HttpServletRequest request, HttpServletResponse response,
                            List<String> errori, Map<String, String> valoriForm,
                            Object risultato, String tipoRisultato) throws IOException {
        try {
            SKUDAO skuDAO = new SKUDAO(connection);
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            List<SKU> tutteLeSku = skuDAO.findAll();
            List<Prodotto> tuttiIProdotti = prodottoDAO.findAllOrfani();

            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext ctx = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("tutteLeSku", tutteLeSku);
            ctx.setVariable("tuttiIProdotti", tuttiIProdotti);
            if (errori != null && !errori.isEmpty()) ctx.setVariable("errori", errori);
            if (valoriForm != null)                  ctx.setVariable("valoriForm", valoriForm);
            if (risultato != null)                   ctx.setVariable("risultato", risultato);
            if (tipoRisultato != null)               ctx.setVariable("tipoRisultato", tipoRisultato);

            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("fornitore/home", ctx, response.getWriter());
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                "Errore nel caricamento della pagina.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
