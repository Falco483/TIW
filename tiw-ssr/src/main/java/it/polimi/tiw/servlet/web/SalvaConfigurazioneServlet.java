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

    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {}
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute("utente");

        String codiceRadice = request.getParameter("codiceRadice");
        String nomeConfigurazione = request.getParameter("nomeConfigurazione");

        if (codiceRadice == null || codiceRadice.isEmpty() || nomeConfigurazione == null || nomeConfigurazione.isEmpty()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "Dati mancanti");
            return;
        }

        Connection conn = this.connection;
        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "No DB connection");
            return;
        }

        try {
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

            List<DettaglioDTO> dettagli = new ArrayList<>();
            BigDecimal prezzoTotale = BigDecimal.ZERO;

            Enumeration<String> params = request.getParameterNames();
            while (params.hasMoreElements()) {
                String pName = params.nextElement();
                if (pName.startsWith("sku_")) {
                    try {
                        int idProdotto = Integer.parseInt(pName.substring(4));
                        int idSku = Integer.parseInt(request.getParameter(pName));
                        
                        BigDecimal prezzoSku = sDao.getPrezzoReale(idSku);
                        if (prezzoSku == null) {
                            conn.rollback();
                            response.sendError(HttpServletResponse.SC_BAD_REQUEST, "SKU non valida");
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

            Configurazione conf = new Configurazione();
            conf.setClienteUsername(utente.username());
            conf.setProdottoRadiceId(radice.getId());
            conf.setNome(nomeConfigurazione);
            conf.setPrezzoTotale(prezzoTotale);

            int idConfig = cDao.inserisciTestata(conf);
            cDao.inserisciDettagliBatch(idConfig, dettagli);

            conn.commit();
            
            // Re-indirizziamo alla home cliente per ora
            response.sendRedirect(request.getContextPath() + "/cliente/configurazioni");

        } catch (SQLException e) {
            try {
                if (conn != null) {
                    conn.rollback();
                }
            } catch (SQLException ex) {
                ex.printStackTrace();
            }
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore salvataggio configurazione");
        } finally {
            try {
                if (conn != null) {
                    conn.setAutoCommit(true);
                }
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }
}
