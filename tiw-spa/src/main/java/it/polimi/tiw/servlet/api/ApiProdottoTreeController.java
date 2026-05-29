package it.polimi.tiw.servlet.api;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.ProdottoComposto;
import it.polimi.tiw.utils.ConnectionFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Controller REST per il salvataggio transazionale di un intero albero di prodotti.
 *
 * Mappato su POST /api/prodotto (singolare) per distinguerlo da /api/prodotti (plurale, GET lista).
 * Riceve un payload JSON rappresentante un ProdottoComposto con figli annidati,
 * lo deserializza sfruttando il polimorfismo Jackson (@JsonTypeInfo sul modello)
 * e lo persiste in un'unica transazione atomica tramite ProdottoDAO.insertTree().
 */
@WebServlet("/api/prodotto")
public class ApiProdottoTreeController extends HttpServlet {

    /**
     * ObjectMapper condiviso e thread-safe.
     * - JavaTimeModule: serializza correttamente LocalDateTime e simili.
     * - WRITE_DATES_AS_TIMESTAMPS disabilitato: produce "2026-05-22T14:30:00" invece di [2026,5,22,14,30].
     */
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Connection connection = null;

    /**
     * Inizializza la servlet e ottiene la connessione al database tramite la ConnectionFactory.
     * @throws ServletException se la connessione al database fallisce.
     */
    @Override
    public void init() throws ServletException {
        try {
            // Usiamo la stessa ConnectionFactory già consolidata in tutto il progetto,
            // che legge i parametri JDBC (driver, url, user, password) dal web.xml.
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
        }
    }

    /**
     * Chiusura sicura della connessione al database quando la servlet viene distrutta.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException ignored) {}
    }

    /**
     * Gestisce la creazione di un nuovo prodotto (Semplice o Composto) e il salvataggio
     * transazionale dell'intero albero di nodi/SKU a esso associato.
     * @param request La richiesta HTTP POST con payload JSON del Prodotto.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore durante la lettura del payload o la scrittura della risposta.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        // --- Fase 0: Configurazione encoding ---
        // Forziamo UTF-8 su entrambi i canali per evitare che caratteri accentati
        // (es. "Scheda Grafica Élite") vengano corrotti durante la serializzazione.
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        // --- Fase 1: Verifica connessione DB ---
        if (connection == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Connessione al DB non disponibile");
            return;
        }

        // --- Fase 2: Deserializzazione del payload JSON ---
        Prodotto prodottoInviato;
        try {
            prodottoInviato = MAPPER.readValue(request.getReader(), Prodotto.class);
        } catch (JsonProcessingException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "JSON malformato o struttura non valida: " + e.getOriginalMessage());
            return;
        }

        // --- Fase 3: Validazione base lato server ---
        if (prodottoInviato.getCodice() <= 0) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Il codice del prodotto deve essere un intero positivo");
            return;
        }
        if (prodottoInviato.getNome() == null || prodottoInviato.getNome().isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Il nome del prodotto è obbligatorio");
            return;
        }

        // --- Fase 3b: Verifica unicità codice ---
        try {
            ProdottoDAO dao = new ProdottoDAO(connection);

            if (dao.findByCodice(prodottoInviato.getCodice()) != null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Il codice " + prodottoInviato.getCodice() + " è già in uso da un altro prodotto");
                return;
            }

            // --- Fase 3c: Validazione specifica per tipo ---
            if (prodottoInviato instanceof it.polimi.tiw.model.ProdottoSemplice pSemplice) {
                // Un prodotto semplice deve avere almeno una SKU associata
                if (pSemplice.getSKUs() == null || pSemplice.getSKUs().isEmpty()) {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                            "Un prodotto semplice deve avere almeno una SKU associata");
                    return;
                }
            }

            // --- Fase 4: Persistenza ---
            int idGenerato = 0;

            if (prodottoInviato instanceof it.polimi.tiw.model.ProdottoSemplice pSemplice) {
                idGenerato = dao.insertSemplice(String.valueOf(pSemplice.getCodice()), pSemplice.getNome(),
                        java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO);

                for (it.polimi.tiw.model.SKU sku : pSemplice.getSKUs()) {
                    dao.addSku(idGenerato, sku.getId());
                }
                // Calcola prezzoMin/prezzoMax dal MIN/MAX dei prezzi delle SKU associate
                dao.calcolaPrezziDaSku(idGenerato);
            } else if (prodottoInviato instanceof ProdottoComposto pComposto) {
                // Validazione V1: prezzoMin >= somma dei prezzoMin dei figli
                // Validazione V2: prezzoMax > prezzoMin
                java.math.BigDecimal pMin = pComposto.getPrezzoMin();
                java.math.BigDecimal pMax = pComposto.getPrezzoMax();
                if (pMin == null) pMin = java.math.BigDecimal.ZERO;
                if (pMax == null) pMax = java.math.BigDecimal.ZERO;

                if (pMax.compareTo(pMin) <= 0) {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                            "Il prezzo massimo deve essere strettamente maggiore del prezzo minimo");
                    return;
                }

                if (pComposto.getFigli() != null && !pComposto.getFigli().isEmpty()) {
                    java.math.BigDecimal sommaMin = java.math.BigDecimal.ZERO;
                    for (Prodotto figlio : pComposto.getFigli()) {
                        Prodotto figlioDb = dao.findById(figlio.getId());
                        if (figlioDb != null && figlioDb.getPrezzoMin() != null) {
                            sommaMin = sommaMin.add(figlioDb.getPrezzoMin());
                        }
                    }
                    if (pMin.compareTo(sommaMin) < 0) {
                        sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                                "Il prezzo minimo deve essere almeno " + sommaMin.setScale(2, java.math.RoundingMode.HALF_UP) + " € (somma dei prezzi min dei sottoprodotti)");
                        return;
                    }
                }

                idGenerato = dao.insertComposto(String.valueOf(pComposto.getCodice()), pComposto.getNome(),
                        pComposto.getDescrizione(), pMin, pMax);

                if (pComposto.getFigli() != null) {
                    for (Prodotto figlio : pComposto.getFigli()) {
                        // Vincolo profondità: il padre appena creato è al livello 1 (radice),
                        // il figlio e il suo sotto-albero non devono superare il livello 4.
                        int profonditaFiglio = dao.calcolaProfondita(figlio.getId());
                        if (1 + profonditaFiglio > 4) {
                            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                                    "Impossibile aggiungere il figlio: la profondità massima dell'albero (4 livelli) verrebbe superata");
                            return;
                        }
                        // Vincolo aciclicità
                        if (!dao.verificaAciclicita(idGenerato, figlio.getId())) {
                            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                                    "Impossibile aggiungere il figlio: si creerebbe un ciclo nell'albero");
                            return;
                        }
                        // Vincolo: il figlio semplice deve avere almeno una SKU
                        Prodotto figlioCompleto = dao.findById(figlio.getId());
                        if (figlioCompleto != null && "SEMPLICE".equals(figlioCompleto.getTipo())) {
                            if (dao.contaSkuAssociate(figlio.getId()) == 0) {
                                sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                                        "Il prodotto semplice \"" + figlioCompleto.getNome() + "\" non ha SKU associate");
                                return;
                            }
                        }
                        dao.addFiglio(idGenerato, figlio.getId());
                    }
                }
            } else {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Tipo prodotto sconosciuto");
                return;
            }

            // --- Fase 5: Risposta di successo ---
            response.setStatus(HttpServletResponse.SC_CREATED);
            MAPPER.writeValue(response.getOutputStream(), Map.of(
                    "success", true,
                    "id_prodotto", idGenerato,
                    "message", "Prodotto salvato con successo"
            ));

        } catch (IllegalStateException e) {
            // Il prodotto figlio appartiene già a un altro padre (vincolo 7)
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel salvataggio del prodotto: " + e.getMessage());
        }
    }

    /**
     * Gestisce il recupero dei dati dei prodotti. Supporta tre modalità:
     * - Albero completo di un prodotto dato l'ID (`?id=...`)
     * - Elenco dei prodotti orfani senza padre (`?orfani=true`)
     * - Elenco dei prodotti radice (default)
     * @param request La richiesta HTTP GET.
     * @param response La risposta HTTP contenente il payload JSON dei risultati.
     * @throws IOException Se si verifica un errore durante la serializzazione.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            ProdottoDAO dao = new ProdottoDAO(connection);
            String idParam = request.getParameter("id");

            if (idParam != null && !idParam.isBlank()) {
                int id = Integer.parseInt(idParam);
                Prodotto albero = dao.getAlberoProdotto(id);
                if (albero == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Prodotto non trovato");
                    return;
                }
                MAPPER.writeValue(response.getOutputStream(), Map.of("data", albero));
            } else if ("true".equals(request.getParameter("orfani"))) {
                List<Prodotto> orfani = dao.findAllOrfani();
                MAPPER.writeValue(response.getOutputStream(), Map.of("data", orfani));
            } else {
                List<Prodotto> radici = dao.getProdottiRadice();
                MAPPER.writeValue(response.getOutputStream(), Map.of("data", radici));
            }
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "ID non valido");
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel recupero dei prodotti: " + e.getMessage());
        }
    }

    /**
     * Aggiorna i campi informativi di base (es. nome, descrizione, prezzoMin/Max) di un prodotto esistente.
     * NON aggiorna la struttura dell'albero o le associazioni.
     * @param request La richiesta HTTP PUT con payload JSON del Prodotto.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore durante la lettura/scrittura dei dati JSON.
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> body = MAPPER.readValue(request.getInputStream(), Map.class);

            int id = ((Number) body.get("id")).intValue();
            String nome = (String) body.get("nome");
            String descrizione = (String) body.get("descrizione");
            BigDecimal prezzoMin = body.get("prezzoMin") != null
                    ? new BigDecimal(body.get("prezzoMin").toString()) : null;
            BigDecimal prezzoMax = body.get("prezzoMax") != null
                    ? new BigDecimal(body.get("prezzoMax").toString()) : null;

            ProdottoDAO dao = new ProdottoDAO(connection);
            Prodotto p = dao.findById(id);
            if (p == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Prodotto non trovato");
                return;
            }
            p.setNome(nome);
            p.setDescrizione(descrizione);
            p.setPrezzoMin(prezzoMin);
            p.setPrezzoMax(prezzoMax);
            dao.updateProdotto(p);

            MAPPER.writeValue(response.getOutputStream(), Map.of("success", true));
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nell'aggiornamento del prodotto: " + e.getMessage());
        } catch (Exception e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Dati di input malformati o non validi");
        }
    }

    /**
     * Gestisce le operazioni di rimozione o disassociazione tramite il parametro 'azione':
     * - 'elimina': Elimina definitivamente un intero prodotto in cascata (default).
     * - 'scollega': Imposta a NULL l'id_padre di un prodotto figlio.
     * - 'scollegaSku': Rimuove l'associazione N:M tra un prodotto semplice e una SKU.
     * @param request La richiesta HTTP DELETE.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore nella serializzazione.
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String idParam = request.getParameter("id");
        if (idParam == null || idParam.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Parametro 'id' mancante");
            return;
        }

        String azione = request.getParameter("azione");
        if (azione == null || azione.isBlank()) {
            azione = "elimina";
        }

        try {
            int id = Integer.parseInt(idParam);
            ProdottoDAO dao = new ProdottoDAO(connection);

            switch (azione) {
                case "elimina" -> {
                    dao.eliminaDefinitivamente(id);
                    MAPPER.writeValue(response.getOutputStream(), Map.of("success", true));
                }
                case "scollega" -> {
                    dao.rimuoviFiglio(id);
                    MAPPER.writeValue(response.getOutputStream(), Map.of("success", true));
                }
                case "scollegaSku" -> {
                    String idSkuParam = request.getParameter("idSku");
                    if (idSkuParam == null || idSkuParam.isBlank()) {
                        sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                                "Parametro 'idSku' mancante per azione scollegaSku");
                        return;
                    }
                    int idSku = Integer.parseInt(idSkuParam);
                    dao.rimuoviAssociazioneSku(id, idSku);
                    MAPPER.writeValue(response.getOutputStream(), Map.of("success", true));
                }
                default -> sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                        "Azione non riconosciuta: " + azione);
            }
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "ID non valido");
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nell'operazione sul prodotto: " + e.getMessage());
        }
    }

    /**
     * Helper per inviare risposte di errore in formato JSON strutturato.
     * Usiamo sempre lo stesso formato {"success": false, "error": "..."} per
     * permettere al frontend di gestire gli errori in modo uniforme.
     */
    private void sendError(HttpServletResponse response, int status, String messaggio)
            throws IOException {
        response.setStatus(status);
        MAPPER.writeValue(response.getOutputStream(), Map.of(
                "success", false,
                "error", messaggio
        ));
    }
}

