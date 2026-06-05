package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.model.ElementoCatalogo;
import it.polimi.tiw.model.Prodotto;
import it.polimi.tiw.model.SKU;
import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.utils.UserRole;
import it.polimi.tiw.utils.ConnectionFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Servlet che gestisce la ricerca degli elementi del catalogo (prodotti e SKU)
 * per il Fornitore (Server-Side Rendering).
 * Permette anche di visualizzare i dettagli completi (albero o scheda SKU) dell'elemento selezionato.
 */
@WebServlet("/fornitore/cerca")
public class CercaCatalogoServlet extends HttpServlet {
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    /**
     * Inizializza il motore Thymeleaf e configura i template resolver.
     */
    @Override
    public void init() throws ServletException {
        this.webApp = JakartaServletWebApplication.buildApplication(getServletContext());

        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(webApp);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");
        
        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    /**
     * Gestisce la richiesta HTTP GET. Esegue la ricerca tramite i DAO se viene specificato
     * il parametro "query", ed eventualmente estrae il dettaglio di un singolo elemento,
     * effettuando infine il rendering tramite Thymeleaf.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errori.
     * @throws IOException in caso di errori di I/O.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        if (session == null || session.getAttribute(UtenteSessionDTO.SESSION_KEY) == null) {
            response.sendRedirect(request.getContextPath() + "/login");
            return;
        }

        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute(UtenteSessionDTO.SESSION_KEY);
        if (utente.ruolo() != UserRole.FORNITORE) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Accesso negato: Solo i fornitori possono cercare nel catalogo.");
            return;
        }

        String query = request.getParameter("query");
        String idDettaglioStr = request.getParameter("idDettaglio");
        String tipoDettaglio = request.getParameter("tipoDettaglio");

        List<ElementoCatalogo> risultati = new ArrayList<>();
        Object dettaglioOggetto = null;
        String tipoRisultato = null;

        try (Connection connection = ConnectionFactory.getConnection(getServletContext())) {
            ProdottoDAO prodottoDAO = new ProdottoDAO(connection);
            SKUDAO skuDAO = new SKUDAO(connection);

            if (query != null && !query.trim().isEmpty()) {
                risultati.addAll(prodottoDAO.search(query));
                risultati.addAll(skuDAO.search(query));
            }

            if (idDettaglioStr != null && tipoDettaglio != null) {
                int idDettaglio = Integer.parseInt(idDettaglioStr);
                if ("SKU".equals(tipoDettaglio)) {
                    dettaglioOggetto = skuDAO.findById(idDettaglio);
                    tipoRisultato = "sku";
                } else {
                    Prodotto p = prodottoDAO.getAlberoProdotto(idDettaglio);
                    dettaglioOggetto = p;
                    tipoRisultato = "COMPOSTO".equals(p.getTipo()) ? "composto" : "semplice";
                }
            }

        } catch (SQLException | ClassNotFoundException | NumberFormatException e) {
            e.printStackTrace();
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Errore nel database");
            return;
        }

        IWebExchange webExchange = webApp.buildExchange(request, response);
        WebContext ctx = new WebContext(webExchange, request.getLocale());
        ctx.setVariable("risultati", risultati);
        ctx.setVariable("query", query);
        ctx.setVariable("risultato", dettaglioOggetto); // Usa "risultato" per riusare il fragment
        ctx.setVariable("tipoRisultato", tipoRisultato);

        String path = "fornitore/cerca";
        response.setContentType("text/html;charset=UTF-8");
        templateEngine.process(path, ctx, response.getWriter());
    }
}
