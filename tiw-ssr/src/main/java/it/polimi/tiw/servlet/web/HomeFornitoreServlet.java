package it.polimi.tiw.servlet.web;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
import it.polimi.tiw.utils.FotoStorage;
import jakarta.servlet.ServletException;
import jakarta.servlet.UnavailableException;
import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.servlet.http.Part;

/**
 * Servlet che gestisce la Home Page dell'interfaccia Fornitore.
 * Consente la creazione di nuove SKU (con caricamento di immagini), la
 * creazione di prodotti semplici
 * (associando SKU esistenti) e di prodotti composti (associando altri prodotti
 * orfani e controllando i vincoli sui prezzi e sulla profondità dell'albero).
 * Implementa il pattern PRG (Post/Redirect/Get) per la gestione dei flussi e
 * dei messaggi.
 */
@WebServlet("/fornitore/home")
@MultipartConfig(maxFileSize = 1024 * 1024 * 5, maxRequestSize = 1024 * 1024 * 10)
public class HomeFornitoreServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private static final String REQ_ERRORI = "errori";
    private static final String REQ_VALORI_FORM = "valoriForm";
    private static final String SESSION_RISULTATO = "home.risultato";
    private static final String SESSION_TIPO_RISULTATO = "home.tipoRisultato";
    private static final String SESSION_PREZZO_MIN_CALC = "home.prezzoMinCalcolato";
    private static final String SESSION_PREZZO_MAX_CALC = "home.prezzoMaxCalcolato";
    private static final String SESSION_ID_FIGLI_SELEZIONATI = "home.idFigliSelezionati";

    private Connection connection = null;
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    /**
     * Inizializza la servlet stabilendo la connessione al database e configurando
     * il motore di template Thymeleaf.
     * 
     * @throws ServletException se si verifica un errore durante l'inizializzazione.
     */
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

    /**
     * Rilascia le risorse allocate, chiudendo la connessione al database.
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
     * Gestisce la richiesta HTTP GET. Legge dalla sessione eventuali messaggi di
     * successo/errore
     * o riepiloghi temporanei di calcolo prezzo (lasciati dagli handler POST
     * secondo il pattern PRG)
     * e renderizza la pagina principale del fornitore.
     * 
     * @param request  la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errore della servlet.
     * @throws IOException      in caso di errori di I/O.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (session != null) {
            // Da AzioneCatalogoServlet (altre servlet)
            String messaggioSuccesso = (String) session.getAttribute("messaggioSuccesso");
            String erroreEliminazione = (String) session.getAttribute("erroreEliminazione");
            // Da handleRicalcolaComposto (PRG ricalcola)
            BigDecimal prezzoMinCalcolato = (BigDecimal) session.getAttribute(SESSION_PREZZO_MIN_CALC);
            BigDecimal prezzoMaxCalcolato = (BigDecimal) session.getAttribute(SESSION_PREZZO_MAX_CALC);
            List<Integer> idFigliSelezionati = (List<Integer>) session.getAttribute(SESSION_ID_FIGLI_SELEZIONATI);
            Map<String, String> valoriForm = (Map<String, String>) session.getAttribute(REQ_VALORI_FORM);

            session.removeAttribute("messaggioSuccesso");
            session.removeAttribute("erroreEliminazione");
            session.removeAttribute(SESSION_PREZZO_MIN_CALC);
            session.removeAttribute(SESSION_PREZZO_MAX_CALC);
            session.removeAttribute(SESSION_ID_FIGLI_SELEZIONATI);
            session.removeAttribute(REQ_VALORI_FORM);
            
            Object risultato = session.getAttribute(SESSION_RISULTATO);
            String tipoRisultato = (String) session.getAttribute(SESSION_TIPO_RISULTATO);
            session.removeAttribute(SESSION_RISULTATO);
            session.removeAttribute(SESSION_TIPO_RISULTATO);

            if (messaggioSuccesso != null)
                request.setAttribute("messaggioSuccesso", messaggioSuccesso);
            if (erroreEliminazione != null)
                request.setAttribute("erroreEliminazione", erroreEliminazione);
            if (prezzoMinCalcolato != null)
                request.setAttribute("prezzoMinCalcolato", prezzoMinCalcolato);
            if (prezzoMaxCalcolato != null)
                request.setAttribute("prezzoMaxCalcolato", prezzoMaxCalcolato);
            if (idFigliSelezionati != null)
                request.setAttribute("idFigliSelezionati", idFigliSelezionati);
            if (valoriForm != null)
                request.setAttribute(REQ_VALORI_FORM, valoriForm);
                
            if (risultato != null)
                request.setAttribute("risultato", risultato);
            if (tipoRisultato != null)
                request.setAttribute("tipoRisultato", tipoRisultato);
        }

        renderHome(request, response);
    }

    /**
     * Gestisce la richiesta HTTP POST. Smista la richiesta all'handler appropriato
     * in base al parametro 'tipoForm' inviato dal client.
     * 
     * @param request  la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errore della servlet.
     * @throws IOException      in caso di errori di I/O.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String tipoForm = request.getParameter("tipoForm");
        if (tipoForm == null) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Parametro tipoForm mancante");
            return;
        }
        switch (tipoForm) {
            case "sku" -> handleCreaSku(request, response);
            case "semplice" -> handleCreaSemplice(request, response);
            case "composto" -> {
                String azione = request.getParameter("azione");
                if ("ricalcola".equals(azione)) {
                    handleRicalcolaComposto(request, response);
                } else {
                    handleCreaComposto(request, response);
                }
            }
            default -> response.sendError(HttpServletResponse.SC_BAD_REQUEST, "tipoForm non valido");
        }
    }

    // -------------------------------------------------------------------------
    // Helper PRG
    // -------------------------------------------------------------------------

    /**
     * Esegue il forward interno per visualizzare gli errori riscontrati nei form,
     * mantenendo in request i valori già inseriti dall'utente per non costringerlo
     * a riscriverli.
     */
    private void forwardConErrore(HttpServletRequest req, HttpServletResponse res,
            List<String> errori, Map<String, String> valoriForm)
            throws IOException, ServletException {
        req.setAttribute(REQ_ERRORI, errori);
        req.setAttribute(REQ_VALORI_FORM, valoriForm);
        renderHome(req, res);
    }

    /**
     * Salva l'oggetto appena creato in sessione e ridirige l'utente verso la
     * servlet dei risultati,
     * implementando il pattern PRG contro i doppi invii accidentali al refresh.
     */
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

    /**
     * Gestisce la creazione di una SKU. Valida i dati obbligatori, esegue il
     * parsing di codice/prezzo,
     * gestisce l'upload del file di immagine (salvato con un UUID univoco nella
     * cartella foto)
     * e inserisce la SKU nel database.
     */
    private void handleCreaSku(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String nomeRaw = request.getParameter("nomeSku");
        String codiceRaw = request.getParameter("codiceSku");
        String descrizioneTecRaw = request.getParameter("descrizioneTecnicaSku");
        String prezzoRaw = request.getParameter("prezzoSku");

        Part filePart = request.getPart("fotografia");
        String percorsoImmagine = null;

        Map<String, String> valoriForm = new HashMap<>();
        valoriForm.put("nomeSku", nomeRaw);
        valoriForm.put("codiceSku", codiceRaw);
        valoriForm.put("descrizioneTecnicaSku", descrizioneTecRaw);
        valoriForm.put("prezzoSku", prezzoRaw);

        List<String> errori = new ArrayList<>();

        if (isBlank(nomeRaw))
            errori.add("Il nome della SKU è obbligatorio.");
        if (isBlank(codiceRaw))
            errori.add("Il codice della SKU è obbligatorio.");
        if (isBlank(descrizioneTecRaw))
            errori.add("La descrizione tecnica della SKU è obbligatoria.");
        if (isBlank(prezzoRaw))
            errori.add("Il prezzo della SKU è obbligatorio.");

        int codice = 0;
        if (!isBlank(codiceRaw)) {
            try {
                codice = Integer.parseInt(codiceRaw.trim());
                if (codice < 1000 || codice > 9999) {
                    errori.add("Il codice deve essere un numero di esattamente 4 cifre (1000-9999).");
                }
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

        if (nomeRaw != null && nomeRaw.trim().length() > 200) {
            errori.add("Il nome supera la lunghezza massima consentita (200 caratteri).");
        }

        if (filePart == null || filePart.getSize() == 0) {
            errori.add("La fotografia è obbligatoria. Seleziona un'immagine.");
        } else if (filePart.getContentType() == null || !filePart.getContentType().startsWith("image/")) {
            errori.add("Il file caricato non è un'immagine valida. Sono ammessi solo file di tipo immagine (JPEG, PNG, ecc.).");
        }

        if (!errori.isEmpty()) {
            forwardConErrore(request, response, errori, valoriForm);
            return;
        }

        try {
            SKUDAO skuDAO = new SKUDAO(connection);

            if (skuDAO.findByCodice(codice) != null) {
                errori.add("Esiste già una SKU con il codice " + codice + ".");
                forwardConErrore(request, response, errori, valoriForm);
                return;
            }

            if (filePart != null && filePart.getSize() > 0) {
                percorsoImmagine = FotoStorage.salva(filePart);
            }

            SKU nuovaSku = new SKU();
            nuovaSku.setCodice(codice);
            nuovaSku.setNome(nomeRaw != null ? nomeRaw.trim() : "");
            nuovaSku.setFotografia(percorsoImmagine);
            nuovaSku.setDescrizioneTecnica(descrizioneTecRaw != null ? descrizioneTecRaw.trim() : "");
            nuovaSku.setPrezzo(prezzo);

            SKU skuCreata = skuDAO.insert(nuovaSku);
            redirectConSuccesso(request, response, skuCreata, "sku");
        } catch (SQLException e) {
            forwardConErrore(request, response,
                    List.of("Errore nel salvataggio della SKU: " + e.getMessage()),
                    valoriForm);
        }
    }

    /**
     * Gestisce la creazione di un Prodotto Semplice. Associa una o più SKU
     * selezionate
     * calcolando automaticamente il prezzo minimo e massimo del prodotto semplice
     * come minimo e massimo dei prezzi delle SKU associate.
     */
    private void handleCreaSemplice(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String codiceRaw = request.getParameter("codiceSemplice");
        String nomeRaw = request.getParameter("nomeSemplice");
        String[] idSkuSelezionateRaw = request.getParameterValues("idSkuSelezionate");

        Map<String, String> valoriForm = new HashMap<>();
        valoriForm.put("codiceSemplice", codiceRaw);
        valoriForm.put("nomeSemplice", nomeRaw);

        List<String> errori = new ArrayList<>();
        if (isBlank(codiceRaw))
            errori.add("Il codice del prodotto semplice è obbligatorio.");
        if (isBlank(nomeRaw))
            errori.add("Il nome del prodotto semplice è obbligatorio.");
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

        int codice = 0;
        if (!isBlank(codiceRaw)) {
            try {
                codice = Integer.parseInt(codiceRaw.trim());
                if (codice < 1000 || codice > 9999) {
                    errori.add("Il codice del prodotto deve essere un numero di esattamente 4 cifre (1000-9999).");
                }
            } catch (NumberFormatException e) {
                errori.add("Il codice del prodotto semplice deve essere un numero intero.");
            }
        }

        if (nomeRaw != null && nomeRaw.trim().length() > 200) {
            errori.add("Il nome del prodotto supera la lunghezza massima consentita (200 caratteri).");
        }

        if (!errori.isEmpty()) {
            forwardConErrore(request, response, errori, valoriForm);
            return;
        }

        try {
            SKUDAO skuDAO = new SKUDAO(connection);
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);

            if (prodottoDAO.findByCodice(codice) != null) {
                errori.add("Esiste già un prodotto con il codice " + codice + ".");
                forwardConErrore(request, response, errori, valoriForm);
                return;
            }

            // Calcola prezzo min e max come MIN/MAX dei prezzi delle SKU selezionate
            BigDecimal prezzoMin = null;
            BigDecimal prezzoMax = null;
            for (int idSku : idSkuList) {
                SKU sku = skuDAO.findById(idSku);
                if (sku == null) {
                    errori.add("SKU con id " + idSku + " non trovata.");
                    forwardConErrore(request, response, errori, valoriForm);
                    return;
                }
                BigDecimal p = sku.getPrezzo();
                if (prezzoMin == null || p.compareTo(prezzoMin) < 0)
                    prezzoMin = p;
                if (prezzoMax == null || p.compareTo(prezzoMax) > 0)
                    prezzoMax = p;
            }

            int id = prodottoDAO.insertSemplice(codiceRaw.trim(), nomeRaw.trim(), prezzoMin, prezzoMax);
            for (int idSku : idSkuList) {
                prodottoDAO.addSku(id, idSku);
            }
            ProdottoSemplice creato = (ProdottoSemplice) prodottoDAO.getAlberoProdotto(id);
            redirectConSuccesso(request, response, creato, "semplice");
        } catch (SQLException e) {
            forwardConErrore(request, response,
                    List.of("Errore nel salvataggio del prodotto semplice: " + e.getMessage()),
                    valoriForm);
        }
    }

    /**
     * Gestisce la creazione di un Prodotto Composto. Assicura che i vincoli sui
     * prezzi siano rispettati
     * (prezzoMin >= somma prezzoMin dei figli, prezzoMax > prezzoMin) e che la
     * profondità dell'albero risultante
     * non superi i 3 livelli strutturali. Gestisce il salvataggio in modalità
     * transazionale (con commit/rollback).
     */
    private void handleCreaComposto(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        String codiceRaw = request.getParameter("codiceComposto");
        String nomeRaw = request.getParameter("nomeComposto");
        String descrizioneRaw = request.getParameter("descrizioneComposto");
        String prezzoMinRaw = request.getParameter("prezzoMinComposto");
        String prezzoMaxRaw = request.getParameter("prezzoMaxComposto");
        String[] idFigliRaw = request.getParameterValues("idFigli");

        Map<String, String> valoriForm = new HashMap<>();
        valoriForm.put("codiceComposto", codiceRaw);
        valoriForm.put("nomeComposto", nomeRaw);
        valoriForm.put("descrizioneComposto", descrizioneRaw);
        valoriForm.put("prezzoMinComposto", prezzoMinRaw);
        valoriForm.put("prezzoMaxComposto", prezzoMaxRaw);

        List<String> errori = new ArrayList<>();
        if (isBlank(codiceRaw))
            errori.add("Il codice del prodotto composto è obbligatorio.");
        if (isBlank(nomeRaw))
            errori.add("Il nome del prodotto composto è obbligatorio.");
        if (isBlank(descrizioneRaw))
            errori.add("La descrizione del prodotto composto è obbligatoria.");
        if (isBlank(prezzoMinRaw))
            errori.add("Il prezzo minimo è obbligatorio.");
        if (isBlank(prezzoMaxRaw))
            errori.add("Il prezzo massimo è obbligatorio.");
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
        if (prezzoMin != null && prezzoMax != null && prezzoMax.compareTo(prezzoMin) <= 0)
            errori.add("Il prezzo massimo deve essere strettamente maggiore del prezzo minimo.");

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

        int codice = 0;
        if (!isBlank(codiceRaw)) {
            try {
                codice = Integer.parseInt(codiceRaw.trim());
                if (codice < 1000 || codice > 9999) {
                    errori.add("Il codice del prodotto deve essere un numero di esattamente 4 cifre (1000-9999).");
                }
            } catch (NumberFormatException e) {
                errori.add("Il codice del prodotto composto deve essere un numero intero.");
            }
        }

        if (nomeRaw != null && nomeRaw.trim().length() > 200) {
            errori.add("Il nome del prodotto supera la lunghezza massima consentita (200 caratteri).");
        }

        request.setAttribute("idFigliSelezionati", idFigliList);

        if (!errori.isEmpty()) {
            forwardConErrore(request, response, errori, valoriForm);
            return;
        }

        boolean autoCommitOriginale = true;
        try {
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);

            if (prodottoDAO.findByCodice(codice) != null) {
                errori.add("Esiste già un prodotto con il codice " + codice + ".");
                forwardConErrore(request, response, errori, valoriForm);
                return;
            }

            BigDecimal sommaMinFigli = BigDecimal.ZERO;
            BigDecimal sommaMaxFigli = BigDecimal.ZERO;
            for (int idFiglio : idFigliList) {
                Prodotto figlio = prodottoDAO.findById(idFiglio);
                if (figlio != null && figlio.getPrezzoMin() != null) {
                    sommaMinFigli = sommaMinFigli.add(figlio.getPrezzoMin());
                    sommaMaxFigli = sommaMaxFigli.add(figlio.getPrezzoMax());
                }
            }
            if (prezzoMin != null && prezzoMin.compareTo(sommaMinFigli) < 0) {
                errori.add("Il prezzo minimo deve essere almeno " + sommaMinFigli
                        + " € (somma dei prezzi minimi dei sotto-prodotti selezionati).");
                forwardConErrore(request, response, errori, valoriForm);
                return;
            }

            autoCommitOriginale = connection.getAutoCommit();
            connection.setAutoCommit(false);

            int id = prodottoDAO.insertComposto(codiceRaw.trim(), nomeRaw.trim(),
                    descrizioneRaw.trim(), prezzoMin, prezzoMax);

            for (int idFiglio : idFigliList) {
                int profonditaFiglio = prodottoDAO.calcolaProfondita(idFiglio);
                if (profonditaFiglio >= 3) {
                    connection.rollback();
                    forwardConErrore(request, response,
                            List.of("Il sottoprodotto selezionato supera la profondità massima consentita per formare un albero di 3 livelli."),
                            valoriForm);
                    return;
                }
                prodottoDAO.addFiglio(id, idFiglio);
            }

            connection.commit();
            Prodotto creato = prodottoDAO.getAlberoProdotto(id);
            redirectConSuccesso(request, response, creato, "composto");
        } catch (IllegalStateException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            forwardConErrore(request, response,
                    List.of("Un sottoprodotto appartiene già a un altro prodotto padre."),
                    valoriForm);
        } catch (SQLException e) {
            try {
                connection.rollback();
            } catch (SQLException ignored) {
            }
            forwardConErrore(request, response,
                    List.of("Errore nel salvataggio del prodotto composto: " + e.getMessage()),
                    valoriForm);
        } finally {
            try {
                connection.setAutoCommit(autoCommitOriginale);
            } catch (SQLException ignored) {
            }
        }
    }

    /**
     * Esegue il calcolo della somma dei prezzi minimi e massimi per i sottoprodotti
     * selezionati.
     * Salva i risultati provvisori in sessione e rimanda l'utente alla GET della
     * home (pattern PRG).
     */
    private void handleRicalcolaComposto(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String[] idFigliRaw = request.getParameterValues("idFigli");
        List<Integer> idFigliList = new ArrayList<>();
        if (idFigliRaw != null) {
            for (String raw : idFigliRaw) {
                try {
                    idFigliList.add(Integer.parseInt(raw));
                } catch (NumberFormatException ignored) {
                }
            }
        }

        BigDecimal sommaMin = BigDecimal.ZERO;
        BigDecimal sommaMax = BigDecimal.ZERO;
        try {
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            for (int idFiglio : idFigliList) {
                Prodotto figlio = prodottoDAO.findById(idFiglio);
                if (figlio != null && figlio.getPrezzoMin() != null) {
                    sommaMin = sommaMin.add(figlio.getPrezzoMin());
                    sommaMax = sommaMax.add(figlio.getPrezzoMax());
                }
            }
        } catch (SQLException e) {
            sommaMin = BigDecimal.ZERO;
            sommaMax = BigDecimal.ZERO;
        }

        HttpSession session = request.getSession(true);
        session.setAttribute(SESSION_PREZZO_MIN_CALC, sommaMin);
        session.setAttribute(SESSION_PREZZO_MAX_CALC, sommaMax);
        session.setAttribute(SESSION_ID_FIGLI_SELEZIONATI, idFigliList);

        Map<String, String> valoriForm = new HashMap<>();
        valoriForm.put("codiceComposto", request.getParameter("codiceComposto"));
        valoriForm.put("nomeComposto", request.getParameter("nomeComposto"));
        valoriForm.put("descrizioneComposto", request.getParameter("descrizioneComposto"));
        valoriForm.put("prezzoMinComposto", request.getParameter("prezzoMinComposto"));
        valoriForm.put("prezzoMaxComposto", request.getParameter("prezzoMaxComposto"));
        session.setAttribute(REQ_VALORI_FORM, valoriForm);

        response.sendRedirect(request.getContextPath() + "/fornitore/home");
    }

    // -------------------------------------------------------------------------
    // Rendering
    // -------------------------------------------------------------------------

    /**
     * Recupera le SKU e i prodotti orfani disponibili dal DB, popola il contesto di
     * Thymeleaf
     * ed effettua il rendering finale del template della home page del fornitore.
     */
    private void renderHome(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        try {
            SKUDAO skuDAO = new SKUDAO(connection);
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            List<SKU> tutteLeSku = skuDAO.findAll();
            List<Prodotto> tuttiIProdotti = prodottoDAO.findAllOrfani();

            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext ctx = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("tutteLeSku", tutteLeSku);
            ctx.setVariable("tuttiIProdotti", tuttiIProdotti);

            List<String> errori = (List<String>) request.getAttribute(REQ_ERRORI);
            Map<String, String> valoriForm = (Map<String, String>) request.getAttribute(REQ_VALORI_FORM);
            String messaggioSuccesso = (String) request.getAttribute("messaggioSuccesso");
            String erroreEliminazione = (String) request.getAttribute("erroreEliminazione");
            BigDecimal prezzoMinCalcolato = (BigDecimal) request.getAttribute("prezzoMinCalcolato");
            BigDecimal prezzoMaxCalcolato = (BigDecimal) request.getAttribute("prezzoMaxCalcolato");
            List<Integer> idFigliSelezionati = (List<Integer>) request.getAttribute("idFigliSelezionati");

            if (errori != null && !errori.isEmpty())
                ctx.setVariable("errori", errori);
            if (valoriForm != null)
                ctx.setVariable("valoriForm", valoriForm);
            if (messaggioSuccesso != null)
                ctx.setVariable("messaggioSuccesso", messaggioSuccesso);
            if (erroreEliminazione != null)
                ctx.setVariable("erroreEliminazione", erroreEliminazione);
            if (prezzoMinCalcolato != null)
                ctx.setVariable("prezzoMinCalcolato", prezzoMinCalcolato);
            if (prezzoMaxCalcolato != null)
                ctx.setVariable("prezzoMaxCalcolato", prezzoMaxCalcolato);
            if (idFigliSelezionati != null)
                ctx.setVariable("idFigliSelezionati", idFigliSelezionati);

            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("fornitore/home", ctx, response.getWriter());
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel caricamento della pagina.");
        }
    }

    /**
     * Controlla se una stringa è nulla, vuota o composta solo da spazi bianchi.
     */
    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
