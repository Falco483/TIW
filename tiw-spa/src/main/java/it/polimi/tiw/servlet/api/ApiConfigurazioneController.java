package it.polimi.tiw.servlet.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dto.DettaglioDTO;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.dto.VoceConfigurazioneDTO;
import it.polimi.tiw.model.Configurazione;
import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.ProdottoComposto;
import it.polimi.tiw.model.ProdottoSemplice;
import it.polimi.tiw.model.SKU;
import it.polimi.tiw.utils.ConnectionFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Controller API per le configurazioni del cliente.
 *
 * Il RoleFilter garantisce che solo i CLIENTI autenticati raggiungano questo servlet.
 *
 * Endpoint:
 *   GET    /api/cliente/configurazioni        → lista configurazioni utente
 *   GET    /api/cliente/configurazioni/{id}   → dettaglio (albero + voci con prezzi congelati)
 *   POST   /api/cliente/configurazioni        → crea nuova configurazione
 *   PUT    /api/cliente/configurazioni/{id}   → modifica configurazione esistente
 *   DELETE /api/cliente/configurazioni/{id}   → elimina configurazione
 */
@WebServlet("/api/cliente/configurazioni/*")
public class ApiConfigurazioneController extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Connection connection = null;

    @Override
    public void init() throws ServletException {
        try {
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
        }
    }

    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {}
    }

    // -------------------------------------------------------------------------
    // GET — lista o dettaglio
    // -------------------------------------------------------------------------

    /**
     * Gestisce le letture, distinguendo in base al pathInfo e ai parametri:
     * <ul>
     *   <li>{@code ?codice=X} → albero del prodotto radice, per la pagina di configurazione;</li>
     *   <li>nessun parametro → lista delle configurazioni dell'utente;</li>
     *   <li>{@code /{id}} → dettaglio di una configurazione (testata, albero e voci con prezzi congelati).</li>
     * </ul>
     *
     * @param request  la richiesta HTTP
     * @param response la risposta HTTP, con corpo JSON
     * @throws IOException se la scrittura della risposta fallisce
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (connection == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        String username = getUsername(request);
        String pathInfo = request.getPathInfo(); // null, "/", o "/{id}"

        try {
            if (pathInfo == null || pathInfo.equals("/")) {

                // GET /configurazioni?codice=X → albero prodotto per la pagina di configurazione
                String codiceParam = request.getParameter("codice");
                if (codiceParam != null) {
                    int codice;
                    try { codice = Integer.parseInt(codiceParam); }
                    catch (NumberFormatException ex) {
                        sendError(response, HttpServletResponse.SC_BAD_REQUEST, "codice non valido");
                        return;
                    }
                    ProdottoDAO pDao = new ProdottoDAO(connection);
                    Prodotto albero = pDao.getAlberoProdottoByCodice(codice);
                    if (albero == null) {
                        sendError(response, HttpServletResponse.SC_NOT_FOUND, "Prodotto non trovato");
                        return;
                    }
                    if (albero.getIdPadre() != null) {
                        sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Il prodotto non è configurabile: non è un prodotto radice");
                        return;
                    }
                    MAPPER.writeValue(response.getOutputStream(), Map.of("data", albero));
                    return;
                }

                // GET /configurazioni → lista configurazioni dell'utente
                ConfigurazioneDAO cDao = new ConfigurazioneDAO(connection);
                List<Configurazione> lista = cDao.getConfigurazioniByUtente(username);
                MAPPER.writeValue(response.getOutputStream(), Map.of("data", lista));

            } else {
                Integer id = parseId(pathInfo);
                if (id == null) {
                    sendError(response, HttpServletResponse.SC_BAD_REQUEST, "ID non valido");
                    return;
                }

                ConfigurazioneDAO cDao = new ConfigurazioneDAO(connection);
                Configurazione conf = cDao.getConfigurazioneById(id, username);
                if (conf == null) {
                    sendError(response, HttpServletResponse.SC_NOT_FOUND, "Configurazione non trovata");
                    return;
                }

                ProdottoDAO pDao = new ProdottoDAO(connection);
                Prodotto albero = pDao.getAlberoProdotto(conf.getProdottoRadiceId());
                Map<Integer, VoceConfigurazioneDTO> voci = cDao.getVociDettaglio(id);

                MAPPER.writeValue(response.getOutputStream(), Map.of(
                        "configurazione", conf,
                        "albero", albero,
                        "voci", voci
                ));
            }
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel recupero delle configurazioni");
        }
    }

    // -------------------------------------------------------------------------
    // POST — crea nuova configurazione
    // -------------------------------------------------------------------------

    /**
     * Crea una nuova configurazione a partire dal JSON inviato (nome, codiceRadice
     * e mappa delle scelte SKU). Valida che il prodotto sia una radice configurabile,
     * costruisce i dettagli con i prezzi congelati e verifica che il totale rientri
     * nella fascia di prezzo prevista prima di salvare testata e dettagli.
     *
     * @param request  la richiesta HTTP, con corpo JSON
     * @param response la risposta HTTP; 201 con l'id creato in caso di successo
     * @throws IOException se la lettura del body o la scrittura della risposta fallisce
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (connection == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        String username = getUsername(request);

        JsonNode body;
        try {
            body = MAPPER.readTree(request.getInputStream());
        } catch (IOException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Body JSON non valido");
            return;
        }

        String nome = body.path("nome").asText().trim();
        if (nome.isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Il nome della configurazione è obbligatorio");
            return;
        }

        JsonNode codiceRadiceNode = body.get("codiceRadice");
        if (codiceRadiceNode == null || !codiceRadiceNode.isInt()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "codiceRadice mancante o non valido");
            return;
        }

        JsonNode scelteNode = body.get("scelte");
        if (scelteNode == null || !scelteNode.isObject()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "scelte mancanti o non valide");
            return;
        }

        try {
            ProdottoDAO pDao = new ProdottoDAO(connection);
            Prodotto albero = pDao.getAlberoProdottoByCodice(codiceRadiceNode.asInt());
            if (albero == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Prodotto non trovato");
                return;
            }
            if (albero.getIdPadre() != null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Il prodotto non è configurabile: non è un prodotto radice");
                return;
            }

            List<DettaglioDTO> dettagli = new ArrayList<>();
            String errore = costruisciDettagli(albero, scelteNode, dettagli);
            if (errore != null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, errore);
                return;
            }

            BigDecimal prezzoTotale = dettagli.stream()
                    .map(DettaglioDTO::getPrezzoUnitarioCongelato)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            errore = verificaFasciaPrezzo(albero, prezzoTotale);
            if (errore != null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, errore);
                return;
            }

            Configurazione conf = new Configurazione();
            conf.setClienteUsername(username);
            conf.setProdottoRadiceId(albero.getId());
            conf.setNome(nome);
            conf.setPrezzoTotale(prezzoTotale);

            ConfigurazioneDAO cDao = new ConfigurazioneDAO(connection);
            int idConfig = cDao.inserisciTestata(conf);
            cDao.inserisciDettagliBatch(idConfig, dettagli);

            response.setStatus(HttpServletResponse.SC_CREATED);
            MAPPER.writeValue(response.getOutputStream(), Map.of("id", idConfig));

        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel salvataggio della configurazione");
        }
    }

    // -------------------------------------------------------------------------
    // PUT — modifica configurazione esistente
    // -------------------------------------------------------------------------

    /**
     * Aggiorna una configurazione esistente dell'utente. Dopo le stesse validazioni
     * della creazione, rigenera i dettagli con il pattern "delete + re-insert" e
     * aggiorna la testata, il tutto in un'unica transazione.
     *
     * @param request  la richiesta HTTP, con corpo JSON
     * @param response la risposta HTTP, con corpo JSON
     * @throws IOException se la lettura del body o la scrittura della risposta fallisce
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (connection == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        String username = getUsername(request);
        Integer id = parseId(request.getPathInfo());
        if (id == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "ID non valido");
            return;
        }

        JsonNode body;
        try {
            body = MAPPER.readTree(request.getInputStream());
        } catch (IOException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Body JSON non valido");
            return;
        }

        String nome = body.path("nome").asText().trim();
        if (nome.isEmpty()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Il nome della configurazione è obbligatorio");
            return;
        }

        JsonNode scelteNode = body.get("scelte");
        if (scelteNode == null || !scelteNode.isObject()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "scelte mancanti o non valide");
            return;
        }

        try {
            ConfigurazioneDAO cDao = new ConfigurazioneDAO(connection);
            Configurazione conf = cDao.getConfigurazioneById(id, username);
            if (conf == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Configurazione non trovata");
                return;
            }

            ProdottoDAO pDao = new ProdottoDAO(connection);
            Prodotto albero = pDao.getAlberoProdotto(conf.getProdottoRadiceId());

            List<DettaglioDTO> dettagli = new ArrayList<>();
            String errore = costruisciDettagli(albero, scelteNode, dettagli);
            if (errore != null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, errore);
                return;
            }

            BigDecimal prezzoTotale = dettagli.stream()
                    .map(DettaglioDTO::getPrezzoUnitarioCongelato)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            errore = verificaFasciaPrezzo(albero, prezzoTotale);
            if (errore != null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, errore);
                return;
            }

            conf.setNome(nome);
            conf.setPrezzoTotale(prezzoTotale);

            // delete + re-insert atomici
            connection.setAutoCommit(false);
            try {
                cDao.deleteDettagli(id);
                cDao.inserisciDettagliBatch(id, dettagli);
                cDao.updateTestata(conf);
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }

            MAPPER.writeValue(response.getOutputStream(), Map.of("success", true));

        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nell'aggiornamento della configurazione");
        }
    }

    // -------------------------------------------------------------------------
    // DELETE
    // -------------------------------------------------------------------------

    /**
     * Elimina una configurazione dell'utente, dopo aver verificato che esista e
     * gli appartenga.
     *
     * @param request  la richiesta HTTP, con l'id nel pathInfo
     * @param response la risposta HTTP, con corpo JSON
     * @throws IOException se la scrittura della risposta fallisce
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (connection == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        String username = getUsername(request);
        Integer id = parseId(request.getPathInfo());
        if (id == null) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "ID non valido");
            return;
        }

        try {
            ConfigurazioneDAO cDao = new ConfigurazioneDAO(connection);
            Configurazione conf = cDao.getConfigurazioneById(id, username);
            if (conf == null) {
                sendError(response, HttpServletResponse.SC_NOT_FOUND, "Configurazione non trovata");
                return;
            }
            cDao.eliminaConfigurazione(id, username);
            MAPPER.writeValue(response.getOutputStream(), Map.of("success", true));

        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nell'eliminazione della configurazione");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers privati
    // -------------------------------------------------------------------------

    /**
     * Percorre ricorsivamente l'albero. Per ogni nodo SEMPLICE verifica che la scelta
     * nel body sia presente e che la SKU appartenga al prodotto, poi aggiunge un
     * DettaglioDTO alla lista.
     *
     * @param nodo     nodo corrente dell'albero (semplice o composto)
     * @param scelte   mappa JSON id_prodotto → id_sku inviata dal client
     * @param dettagli lista in cui accumulare i dettagli validati
     * @return null se tutto valido, altrimenti il messaggio di errore
     */
    private String costruisciDettagli(Prodotto nodo, JsonNode scelte, List<DettaglioDTO> dettagli) {
        if (nodo instanceof ProdottoSemplice ps) {
            String chiave = String.valueOf(ps.getId());
            JsonNode skuIdNode = scelte.get(chiave);
            if (skuIdNode == null || !skuIdNode.isInt()) {
                return "Scelta SKU mancante per il prodotto: " + ps.getNome();
            }
            int skuId = skuIdNode.asInt();
            SKU skuScelta = ps.getSKUs().stream()
                    .filter(s -> s.getId() == skuId)
                    .findFirst()
                    .orElse(null);
            if (skuScelta == null) {
                return "La SKU scelta non appartiene al prodotto: " + ps.getNome();
            }
            dettagli.add(new DettaglioDTO(ps.getId(), skuId, skuScelta.getPrezzo()));

        } else if (nodo instanceof ProdottoComposto pc) {
            for (Prodotto figlio : pc.getFigli()) {
                String errore = costruisciDettagli(figlio, scelte, dettagli);
                if (errore != null) return errore;
            }
        }
        return null;
    }

    /**
     * Verifica che il prezzo totale rientri nella fascia del prodotto radice.
     *
     * @param radice       prodotto radice, da cui leggere prezzoMin e prezzoMax
     * @param prezzoTotale somma dei prezzi congelati delle SKU scelte
     * @return null se prezzoTotale è nel range [prezzoMin, prezzoMax], altrimenti messaggio di errore
     */
    private String verificaFasciaPrezzo(Prodotto radice, BigDecimal prezzoTotale) {
        BigDecimal min = radice.getPrezzoMin();
        BigDecimal max = radice.getPrezzoMax();
        if (min != null && prezzoTotale.compareTo(min) < 0)
            return "Il prezzo totale (" + prezzoTotale + " €) è inferiore al minimo consentito (" + min + " €)";
        if (max != null && prezzoTotale.compareTo(max) > 0)
            return "Il prezzo totale (" + prezzoTotale + " €) supera il massimo consentito (" + max + " €)";
        return null;
    }

    private String getUsername(HttpServletRequest request) {
        UtenteSessionDTO utente = (UtenteSessionDTO) request.getSession(false)
                .getAttribute(UtenteSessionDTO.SESSION_KEY);
        return utente.username();
    }

    /** Estrae l'ID intero dal pathInfo ("/{id}") — restituisce null se malformato. */
    private Integer parseId(String pathInfo) {
        if (pathInfo == null || pathInfo.equals("/")) return null;
        try {
            return Integer.parseInt(pathInfo, 1, pathInfo.length(), 10);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void sendError(HttpServletResponse response, int status, String messaggio)
            throws IOException {
        response.setStatus(status);
        MAPPER.writeValue(response.getOutputStream(), Map.of("errore", messaggio));
    }
}
