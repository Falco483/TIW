package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.dto.DettaglioDTO;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Servlet che gestisce il salvataggio di una configurazione.
 * Gestisce sia il primo salvataggio (INSERT) che l'aggiornamento di una
 * configurazione esistente (UPDATE).
 * Implementa il "Price Snapshotting" ricalcolando il prezzo totale in base ai
 * prezzi attuali del catalogo.
 */
@WebServlet("/cliente/salva")
public class SalvaConfigurazioneServlet extends HttpServlet {

    private Connection connection = null;

    @Override
    public void init() throws ServletException {
        try {
            connection = it.polimi.tiw.utils.ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
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
        } catch (SQLException e) {
        }
    }

    /**
     * Gestisce il salvataggio dei dati inviati dal form di configurazione.
     * Recupera le SKU scelte, ricalcola il prezzo totale, e aggiorna o inserisce i
     * dati nel DB in modo transazionale.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");

        String codiceRadiceStr = request.getParameter("codiceRadice");
        String nomeConfigurazione = request.getParameter("nomeConfigurazione");
        String idModificaStr = request.getParameter("idConfig");

        // Validazione codice radice
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

        // Raccoglie apertoSet dai parametri hidden del form
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

        // Validazione nome
        if (nomeConfigurazione == null || nomeConfigurazione.trim().isEmpty()) {
            ritornaAllaFormConErrore(request, response, "Il nome della configurazione non può essere vuoto", codiceRadice, apertoSet);
            return;
        }

        // Determina se è una modifica o un nuovo inserimento in base alla presenza di
        // idModifica
        boolean isModifica = (idModificaStr != null && !idModificaStr.isEmpty());
        int idModifica = 0;
        if (isModifica) {
            try {
                idModifica = Integer.parseInt(idModificaStr);
            } catch (NumberFormatException e) {
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "ID modifica non valido");
                return;
            }
        }

        Connection conn = this.connection;
        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No DB connection");
            return;
        }

        try {
            // Avvio transazione
            conn.setAutoCommit(false);

            ProdottoDAO pDao = new ProdottoDAO(conn);
            SKUDAO sDao = new SKUDAO(conn);
            ConfigurazioneDAO cDao = new ConfigurazioneDAO(conn);

            if (isModifica) {
                Configurazione confEsistente = cDao.getConfigurazioneById(idModifica, utente.username());
                if (confEsistente == null) {
                    conn.rollback();
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Configurazione non trovata o non di proprietà");
                    return;
                }
            }

            Prodotto radice = pDao.getAlberoProdottoByCodice(codiceRadice);
            if (radice == null) {
                conn.rollback();
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Prodotto radice non valido");
                return;
            }

            int expectedSkuCount = contaProdottiSemplici(radice);
            int actualSkuCount = 0;

            // Raccoglie le SKU selezionate dai parametri (es. sku_123=456) e ricalcola il
            // prezzo totale
            List<DettaglioDTO> dettagli = new ArrayList<>();
            BigDecimal prezzoTotale = BigDecimal.ZERO;

            Enumeration<String> params = request.getParameterNames();
            while (params.hasMoreElements()) {
                String pName = params.nextElement();
                if (pName.startsWith("sku_")) {
                    actualSkuCount++;
                    try {
                        int idProdotto = Integer.parseInt(pName.substring(4));
                        int idSku = Integer.parseInt(request.getParameter(pName));

                        // Price Snapshotting: legge il prezzo attuale dal catalogo
                        BigDecimal prezzoSku = sDao.getPrezzoReale(idSku);
                        if (prezzoSku == null) {
                            conn.rollback();
                            ritornaAllaFormConErrore(request, response, "Una delle SKU selezionate non è più valida o non esiste", codiceRadice, apertoSet);
                            return;
                        }

                        DettaglioDTO dto = new DettaglioDTO(idProdotto, idSku, prezzoSku);
                        dettagli.add(dto);
                        prezzoTotale = prezzoTotale.add(prezzoSku);
                    } catch (NumberFormatException e) {
                        conn.rollback();
                        ritornaAllaFormConErrore(request, response, "inserire tutte le opzioni", codiceRadice, apertoSet);
                        return;
                    }
                }
            }

            if (dettagli.isEmpty()) {
                conn.rollback();
                ritornaAllaFormConErrore(request, response, "inserire tutte le opzioni", codiceRadice, apertoSet);
                return;
            }

            if (actualSkuCount != expectedSkuCount) {
                conn.rollback();
                ritornaAllaFormConErrore(request, response, "inserire tutte le opzioni", codiceRadice, apertoSet);
                return;
            }

            int idRedir;
            if (isModifica) {
                // --- MODIFICA: aggiorna testata, cancella vecchi dettagli, inserisce nuovi ---
                Configurazione conf = new Configurazione();
                conf.setId(idModifica);
                conf.setClienteUsername(utente.username());
                conf.setNome(nomeConfigurazione);
                conf.setPrezzoTotale(prezzoTotale);

                cDao.updateTestata(conf);
                cDao.deleteDettagli(idModifica);
                cDao.inserisciDettagliBatch(idModifica, dettagli);
                idRedir = idModifica;
            } else {
                // --- NUOVO INSERIMENTO: crea testata e poi inserisce dettagli in batch ---
                Configurazione conf = new Configurazione();
                conf.setClienteUsername(utente.username());
                conf.setProdottoRadiceId(radice.getId());
                conf.setNome(nomeConfigurazione);
                conf.setPrezzoTotale(prezzoTotale);

                idRedir = cDao.inserisciTestata(conf);
                cDao.inserisciDettagliBatch(idRedir, dettagli);
            }

            // Fine transazione
            conn.commit();

            // Pulizia degli attributi di sessione lasciati dalla navigazione progressiva
            session.removeAttribute("configura.mappaScelte");
            session.removeAttribute("configura.errore");
            session.removeAttribute("configura.nomeInserito");

            response.sendRedirect(request.getContextPath() + "/cliente/dettaglio?idConfig=" + idRedir);

        } catch (SQLException e) {
            try {
                if (conn != null)
                    conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore salvataggio configurazione");
        } finally {
            try {
                if (conn != null)
                    conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private int contaProdottiSemplici(Prodotto nodo) {
        if ("SEMPLICE".equals(nodo.getTipo())) {
            return 1;
        } else if ("COMPOSTO".equals(nodo.getTipo()) && nodo instanceof ProdottoComposto) {
            int count = 0;
            for (Prodotto figlio : ((ProdottoComposto) nodo).getFigli()) {
                count += contaProdottiSemplici(figlio);
            }
            return count;
        }
        return 0;
    }

    private void ritornaAllaFormConErrore(HttpServletRequest request, HttpServletResponse response,
            String messaggio, int codiceRadice, Set<Integer> apertoSet) throws IOException {

        // Ricostruisce mappaScelte dai parametri sku_* del form
        Map<Integer, Integer> mappaScelte = new HashMap<>();
        Enumeration<String> params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String pName = params.nextElement();
            if (pName.startsWith("sku_")) {
                try {
                    int idProdotto = Integer.parseInt(pName.substring(4));
                    int idSku = Integer.parseInt(request.getParameter(pName));
                    mappaScelte.put(idProdotto, idSku);
                } catch (NumberFormatException e) {
                    // ignora parametri malformati
                }
            }
        }

        // Salva tutto in sessione come flash attributes: ConfiguraServlet.doGet li leggerà e li rimuoverà
        HttpSession session = request.getSession();
        session.setAttribute("configura.mappaScelte", mappaScelte);
        session.setAttribute("configura.errore", messaggio);
        session.setAttribute("configura.nomeInserito", request.getParameter("nomeConfigurazione"));

        // Ricostruisce l'URL di redirect con codice, apertoSet e idConfig se presente
        StringBuilder url = new StringBuilder(request.getContextPath() + "/cliente/configura");
        url.append("?codice=").append(codiceRadice);
        for (int id : apertoSet) {
            url.append("&aperto=").append(id);
        }
        String idConfigStr = request.getParameter("idConfig");
        if (idConfigStr != null && !idConfigStr.isEmpty()) {
            url.append("&idConfig=").append(idConfigStr);
        }
        response.sendRedirect(url.toString());
    }
}
