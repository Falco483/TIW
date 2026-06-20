package it.polimi.tiw.servlet.api;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.SKU;
import it.polimi.tiw.utils.ConnectionFactory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.core.type.TypeReference;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Controller API per la sincronizzazione in blocco dell'editor ad albero (SPA).
 *
 * Riceve un elenco ordinato di azioni (creazione/modifica/collegamento di nodi e
 * SKU) e le applica in un'unica transazione: o vanno a buon fine tutte, o si fa
 * rollback. Gli ID temporanei generati dal client ("temp_...") vengono risolti
 * negli ID reali man mano che i nodi sono creati.
 */
@WebServlet("/api/sync")
public class ApiSyncController extends HttpServlet {

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
            connection = ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
        }
    }

    /**
     * Termina il ciclo di vita della servlet chiudendo in modo sicuro la connessione al database.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException ignored) {}
    }

    /**
     * Gestisce la sincronizzazione in blocco delle azioni effettuate dall'utente sull'editor ad albero.
     * Riceve un array JSON di azioni (es. CREATE_NODE, UPDATE_NODE, LINK_NODE) e le esegue
     * in ordine all'interno di un'unica transazione sul database.
     * In caso di errore durante l'esecuzione di una qualsiasi azione, effettua il rollback completo.
     * @param request La richiesta HTTP POST con il payload JSON.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore durante la deserializzazione JSON.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        request.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        if (connection == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Connessione al DB non disponibile");
            return;
        }

        try {
            List<Map<String, Object>> actions = MAPPER.readValue(request.getInputStream(), new TypeReference<>() {});
            
            Map<String, Integer> tempToRealIds = new HashMap<>(); // per i Prodotti
            Map<String, Integer> tempToRealSkuIds = new HashMap<>(); // per le SKU
            Set<Integer> sempliciCreati = new java.util.HashSet<>(); // ID reali dei SEMPLICE creati

            ProdottoDAO prodDao = new ProdottoDAO(connection);
            SKUDAO skuDao = new SKUDAO(connection);
            
            // Inizio Transazione
            connection.setAutoCommit(false);

            for (Map<String, Object> actionObj : actions) {
                String action = (String) actionObj.get("action");
                
                switch (action) {
                    case "CREATE_NODE" -> {
                        String tempId = (String) actionObj.get("tempId");
                        String tipo = (String) actionObj.get("tipo");
                        String codice = actionObj.get("codice").toString();
                        String nome = (String) actionObj.get("nome");

                        // Vincolo 9: verifica unicità codice prodotto
                        if (prodDao.findByCodice(Integer.parseInt(codice)) != null) {
                            throw new IllegalArgumentException(
                                    "Il codice prodotto " + codice + " è già in uso");
                        }
                        
                        int newId;
                        if ("COMPOSTO".equals(tipo)) {
                            String desc = (String) actionObj.get("descrizione");
                            BigDecimal pMin = getBigDecimal(actionObj.get("prezzoMin"));
                            BigDecimal pMax = getBigDecimal(actionObj.get("prezzoMax"));
                            newId = prodDao.insertComposto(codice, nome, desc, pMin, pMax);
                        } else {
                            newId = prodDao.insertSemplice(codice, nome, BigDecimal.ZERO, BigDecimal.ZERO);
                            sempliciCreati.add(newId);
                        }
                        tempToRealIds.put(tempId, newId);
                        
                        // Link al padre se specificato
                        Object parentObj = actionObj.get("parentId");
                        if (parentObj != null) {
                            int realParentId = resolveId(parentObj, tempToRealIds);
                            // Vincolo profondità: massimo 3 livelli
                            int livelloPadre = prodDao.calcolaLivello(realParentId);
                            if (livelloPadre + 1 > 3) {
                                throw new IllegalArgumentException(
                                        "Impossibile aggiungere il nodo: profondità massima (3 livelli) superata");
                            }
                            prodDao.addFiglio(realParentId, newId);
                        }
                    }
                    case "UPDATE_NODE" -> {
                        int realId = resolveId(actionObj.get("id"), tempToRealIds);
                        Prodotto p = prodDao.findById(realId);
                        if (p != null) {
                            if (actionObj.containsKey("nome")) p.setNome((String) actionObj.get("nome"));
                            if (actionObj.containsKey("descrizione")) p.setDescrizione((String) actionObj.get("descrizione"));
                            if (actionObj.containsKey("prezzoMin")) p.setPrezzoMin(getBigDecimal(actionObj.get("prezzoMin")));
                            if (actionObj.containsKey("prezzoMax")) p.setPrezzoMax(getBigDecimal(actionObj.get("prezzoMax")));
                            prodDao.updateProdotto(p);
                        }
                    }
                    case "LINK_NODE" -> {
                        int parentId = resolveId(actionObj.get("parentId"), tempToRealIds);
                        int childId = resolveId(actionObj.get("childId"), tempToRealIds);
                        // Vincolo profondità: livello padre + profondità sottoalbero figlio <= 3
                        int livelloPadre = prodDao.calcolaLivello(parentId);
                        int profonditaFiglio = prodDao.calcolaProfondita(childId);
                        if (livelloPadre + profonditaFiglio > 3) {
                            throw new IllegalArgumentException(
                                    "Impossibile collegare il nodo: profondità massima (3 livelli) superata");
                        }
                        // Vincolo aciclicità
                        if (!prodDao.verificaAciclicita(parentId, childId)) {
                            throw new IllegalArgumentException(
                                    "Impossibile collegare il nodo: si creerebbe un ciclo nell'albero");
                        }
                        prodDao.addFiglio(parentId, childId);
                    }
                    case "UNLINK_NODE" -> {
                        int realId = resolveId(actionObj.get("id"), tempToRealIds);
                        prodDao.rimuoviFiglio(realId);
                    }
                    case "DELETE_NODE" -> {
                        int realId = resolveId(actionObj.get("id"), tempToRealIds);
                        prodDao.eliminaDefinitivamente(realId);
                    }
                    case "CREATE_SKU" -> {
                        String tempId = (String) actionObj.get("tempId");
                        int codiceInt = Integer.parseInt(actionObj.get("codice").toString());

                        // Vincolo: codice massimo 4 cifre
                        if (codiceInt <= 0 || codiceInt > 9999) {
                            throw new IllegalArgumentException(
                                    "Il codice SKU deve essere un intero positivo di massimo 4 cifre (1-9999)");
                        }

                        // Vincolo 9: verifica unicità codice SKU
                        if (skuDao.findByCodice(codiceInt) != null) {
                            throw new IllegalArgumentException(
                                    "Il codice SKU " + codiceInt + " è già in uso");
                        }

                        SKU sku = new SKU();
                        sku.setCodice(codiceInt);
                        sku.setNome((String) actionObj.get("nome"));
                        sku.setPrezzo(getBigDecimal(actionObj.get("prezzo")));
                        if (actionObj.containsKey("descrizioneTecnica")) sku.setDescrizioneTecnica((String) actionObj.get("descrizioneTecnica"));
                        
                        SKU savedSku = skuDao.insert(sku);
                        tempToRealSkuIds.put(tempId, savedSku.getId());
                        
                        // Associa al prodotto padre, se specificato
                        Object parentObj = actionObj.get("parentId");
                        if (parentObj != null) {
                            int realParentId = resolveId(parentObj, tempToRealIds);
                            prodDao.addSku(realParentId, savedSku.getId());
                        }
                    }
                    case "ADD_SKU" -> {
                        int parentId = resolveId(actionObj.get("parentId"), tempToRealIds);
                        int skuId = resolveId(actionObj.get("skuId"), tempToRealSkuIds);
                        prodDao.addSku(parentId, skuId);
                    }
                    case "UNLINK_SKU" -> {
                        int parentId = resolveId(actionObj.get("parentId"), tempToRealIds);
                        int skuId = resolveId(actionObj.get("skuId"), tempToRealSkuIds);
                        prodDao.rimuoviAssociazioneSku(parentId, skuId);
                    }
                    case "DELETE_SKU" -> {
                        int skuId = resolveId(actionObj.get("skuId"), tempToRealSkuIds);
                        if (skuDao.countUsage(skuId) == 0) {
                            skuDao.eliminaDefinitivamente(skuId);
                        }
                    }
                }
            }
            
            // Ricalcola prezzoMin/prezzoMax solo per i SEMPLICE creati in questa transazione
            for (int idSemplice : sempliciCreati) {
                prodDao.calcolaPrezziDaSku(idSemplice);
            }

            connection.commit();

            response.setStatus(HttpServletResponse.SC_OK);
            MAPPER.writeValue(response.getOutputStream(), Map.of("success", true, "message", "Sincronizzazione completata"));

        } catch (IllegalArgumentException | IllegalStateException e) {
            // Vincoli di dominio violati (livello, ciclo, codice duplicato, figlio già assegnato)
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException ex) { /* ignore */ }
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            try {
                if (connection != null) connection.rollback();
            } catch (SQLException ex) {
                // ignore
            }
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore durante la sincronizzazione: " + e.getMessage());
        } finally {
            try {
                if (connection != null) connection.setAutoCommit(true);
            } catch (SQLException ex) {
                // ignore
            }
        }
    }

    /**
     * Risolve un identificativo (ID) fornito dal client, che può essere un ID reale (intero) o temporaneo (stringa "temp_...").
     * Se è temporaneo, recupera l'ID reale generato durante la transazione dalla mappa.
     * @param idObj L'oggetto contenente l'ID.
     * @param tempMap La mappa che associa gli ID temporanei a quelli reali generati dal DB.
     * @return L'ID reale intero, oppure -1 se non valido o non trovato.
     */
    private int resolveId(Object idObj, Map<String, Integer> tempMap) {
        if (idObj == null) return -1;
        String idStr = idObj.toString();
        if (idStr.startsWith("temp_")) {
            return tempMap.getOrDefault(idStr, -1);
        }
        return Integer.parseInt(idStr);
    }

    /**
     * Converte un valore generico in BigDecimal. Gestisce valori nulli e stringhe vuote.
     * @param val Il valore da convertire.
     * @return Il valore decimale corrispondente, o null se assente.
     */
    private BigDecimal getBigDecimal(Object val) {
        if (val == null) return null;
        if (val.toString().isBlank()) return null;
        return new BigDecimal(val.toString());
    }

    /**
     * Invia un messaggio di errore strutturato al client in formato JSON.
     * @param response L'oggetto HttpServletResponse per l'invio.
     * @param status Il codice HTTP di stato.
     * @param messaggio Il dettaglio dell'errore.
     * @throws IOException Se si verifica un problema di scrittura della risposta.
     */
    private void sendError(HttpServletResponse response, int status, String messaggio) throws IOException {
        response.setStatus(status);
        MAPPER.writeValue(response.getOutputStream(), Map.of("success", false, "error", messaggio));
    }
}
