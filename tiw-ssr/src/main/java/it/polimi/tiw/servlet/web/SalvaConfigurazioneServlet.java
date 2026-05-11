package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ConfigurazioneDAO;
import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dao.SkuDAO;
import it.polimi.tiw.dto.DettaglioDTO;
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
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

/**
 * Servlet che gestisce il salvataggio di una configurazione.
 * Gestisce sia il primo salvataggio (INSERT) che l'aggiornamento di una configurazione esistente (UPDATE).
 * Implementa il "Price Snapshotting" ricalcolando il prezzo totale in base ai prezzi attuali del catalogo.
 */
@WebServlet("/cliente/salva")
public class SalvaConfigurazioneServlet extends HttpServlet {

    private Connection connection = null;

    /**
     * Inizializza la servlet stabilendo la connessione al database.
     */
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
        } catch (SQLException e) {}
    }

    /**
     * Gestisce il salvataggio dei dati inviati dal form di configurazione.
     * Recupera le SKU scelte, ricalcola il prezzo totale, e aggiorna o inserisce i dati nel DB in modo transazionale.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");

        String codiceRadiceStr = request.getParameter("codiceRadice");
        String nomeConfigurazione = request.getParameter("nomeConfigurazione");
        String idModificaStr = request.getParameter("idModifica");

        // Validazione parametri obbligatori
        if (codiceRadiceStr == null || codiceRadiceStr.isEmpty() || nomeConfigurazione == null || nomeConfigurazione.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Dati mancanti");
            return;
        }

        int codiceRadice;
        try {
            codiceRadice = Integer.parseInt(codiceRadiceStr);
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Codice radice non valido");
            return;
        }

        // Determina se è una modifica o un nuovo inserimento in base alla presenza di idModifica
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
            SkuDAO sDao = new SkuDAO(conn);
            ConfigurazioneDAO cDao = new ConfigurazioneDAO(conn);

            Prodotto radice = pDao.getAlberoProdotto(codiceRadice);
            if (radice == null) {
                conn.rollback();
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Prodotto radice non valido");
                return;
            }

            // Raccoglie le SKU selezionate dai parametri (es. sku_123=456) e ricalcola il prezzo totale
            List<DettaglioDTO> dettagli = new ArrayList<>();
            BigDecimal prezzoTotale = BigDecimal.ZERO;

            Enumeration<String> params = request.getParameterNames();
            while (params.hasMoreElements()) {
                String pName = params.nextElement();
                if (pName.startsWith("sku_")) {
                    try {
                        int idProdotto = Integer.parseInt(pName.substring(4));
                        int idSku = Integer.parseInt(request.getParameter(pName));
                        
                        // Price Snapshotting: legge il prezzo attuale dal catalogo
                        BigDecimal prezzoSku = sDao.getPrezzoReale(idSku);
                        if (prezzoSku == null) {
                            conn.rollback();
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Una delle SKU selezionate non è più valida");
                            return;
                        }

                        DettaglioDTO dto = new DettaglioDTO(idProdotto, idSku, prezzoSku);
                        dettagli.add(dto);
                        prezzoTotale = prezzoTotale.add(prezzoSku);
                    } catch (NumberFormatException e) {
                        conn.rollback();
                        response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Formato parametri errato");
                        return;
                    }
                }
            }

            if (dettagli.isEmpty()) {
                conn.rollback();
                response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Nessuna SKU selezionata");
                return;
            }

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
            } else {
                // --- NUOVO INSERIMENTO: crea testata e poi inserisce dettagli in batch ---
                Configurazione conf = new Configurazione();
                conf.setClienteUsername(utente.username());
                conf.setProdottoRadiceId(radice.getId());
                conf.setNome(nomeConfigurazione);
                conf.setPrezzoTotale(prezzoTotale);

                int idConfig = cDao.inserisciTestata(conf);
                cDao.inserisciDettagliBatch(idConfig, dettagli);
            }

            // Fine transazione
            conn.commit();
            
            // Redirect alla lista delle configurazioni dell'utente
            response.sendRedirect(request.getContextPath() + "/cliente/configurazioni");

        } catch (SQLException e) {
            try {
                if (conn != null) conn.rollback();
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore salvataggio configurazione");
        } finally {
            try {
                if (conn != null) conn.setAutoCommit(true);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}

