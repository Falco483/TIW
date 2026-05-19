package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.utils.UserRole;
import it.polimi.tiw.utils.ConnectionFactory;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

@WebServlet("/fornitore/azione")
public class AzioneCatalogoServlet extends HttpServlet {

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(UtenteSessionDTO.SESSION_KEY) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute(UtenteSessionDTO.SESSION_KEY);
        if (utente.ruolo() != UserRole.FORNITORE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Accesso negato.");
            return;
        }

        // Verifica CSRF
        String expectedCsrfToken = (String) session.getAttribute("csrfToken");
        String actualCsrfToken = request.getParameter("_csrf");
        if (expectedCsrfToken == null || !expectedCsrfToken.equals(actualCsrfToken)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Token CSRF non valido.");
            return;
        }

        String azione = request.getParameter("azione"); // "RIMUOVI" o "ELIMINA"
        String tipoOggetto = request.getParameter("tipoOggetto"); // "SKU", "SEMPLICE", "COMPOSTO"
        String idOggettoStr = request.getParameter("idOggetto");
        String idPadreStr = request.getParameter("idPadre");

        try (Connection connection = ConnectionFactory.getConnection(getServletContext())) {
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            SKUDAO skuDAO = new SKUDAO(connection);
            it.polimi.tiw.dao.ConfigurazioneDAO configurazioneDAO = new it.polimi.tiw.dao.ConfigurazioneDAO(connection);

            int idOggetto = Integer.parseInt(idOggettoStr);

            if ("ELIMINA".equals(azione)) {
                try {
                    connection.setAutoCommit(false);

                    // Fase 1: Pulizia configurazioni clienti (Cascata gestita in Java)
                    configurazioneDAO.eliminaConfigurazioniPerComponente(idOggetto, tipoOggetto);

                    // Fase 2: Eliminazione fisica dell'oggetto
                    if ("SKU".equals(tipoOggetto)) {
                        skuDAO.eliminaDefinitivamente(idOggetto);
                    } else {
                        prodottoDAO.eliminaDefinitivamente(idOggetto);
                    }

                    // Fase 3: Successo
                    connection.commit();
                    session.setAttribute("messaggioSuccesso", "Elemento eliminato con successo dal catalogo. Tutte le configurazioni clienti collegate sono state rimosse.");
                } catch (SQLException e) {
                    connection.rollback();
                    throw e;
                } finally {
                    connection.setAutoCommit(true);
                }
            } else if ("RIMUOVI".equals(azione)) {
                int idPadre = Integer.parseInt(idPadreStr);
                if ("SKU".equals(tipoOggetto)) {
                    prodottoDAO.rimuoviAssociazioneSku(idPadre, idOggetto);
                } else {
                    prodottoDAO.rimuoviFiglio(idOggetto);
                }
            }
        } catch (SQLIntegrityConstraintViolationException e) {
            session.setAttribute("erroreEliminazione", "Impossibile eliminare: l'oggetto fa parte di una configurazione cliente.");
        } catch (SQLException | ClassNotFoundException | NumberFormatException e) {
            if (e instanceof SQLException && "23000".equals(((SQLException) e).getSQLState())) {
                session.setAttribute("erroreEliminazione", "Impossibile eliminare: l'oggetto è in uso.");
            } else {
                e.printStackTrace();
                session.setAttribute("erroreEliminazione", "Errore durante l'operazione sul database.");
            }
        }

        response.sendRedirect(request.getContextPath() + "/fornitore/home");
    }
}
