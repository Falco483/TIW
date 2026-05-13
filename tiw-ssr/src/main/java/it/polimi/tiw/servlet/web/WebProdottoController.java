package it.polimi.tiw.servlet.web;

import it.polimi.tiw.dao.ProdottoDAO;
import it.polimi.tiw.model.ProdottoComposto;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Servlet che gestisce la Home Page del cliente.
 * Mostra la lista dei prodotti radice (composti) disponibili per la configurazione,
 * implementando la paginazione lato server.
 */
@WebServlet("/cliente/home")
public class WebProdottoController extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private Connection connection = null;
    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    /**
     * Inizializza la servlet stabilendo la connessione al database e configurando Thymeleaf.
     */
    @Override
    public void init() throws ServletException {
        try {
            connection = it.polimi.tiw.utils.ConnectionFactory.getConnection(getServletContext());
            
            webApp = JakartaServletWebApplication.buildApplication(getServletContext());
            WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(webApp);
            resolver.setTemplateMode(TemplateMode.HTML);
            resolver.setPrefix("/WEB-INF/templates/");
            resolver.setSuffix(".html");
            templateEngine = new TemplateEngine();
            templateEngine.setTemplateResolver(resolver);
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Inizializzazione fallita");
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
     * Gestisce la richiesta GET della home cliente.
     * Calcola la pagina corrente, recupera i prodotti paginati dal DAO e renderizza la home.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        Connection conn = this.connection;

        if (conn == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Connessione al DB non disponibile");
            return;
        }

        try {
            // Gestione della paginazione
            int pagina = 1;
            String paginaParam = request.getParameter("pagina");
            if (paginaParam != null && !paginaParam.isEmpty()) {
                try {
                    pagina = Integer.parseInt(paginaParam);
                    if (pagina < 1) pagina = 1;
                } catch (NumberFormatException e) {
                    pagina = 1;
                }
            }

            ProdottoDAO dao = new ProdottoDAO(conn);
            int totale = dao.contaProdottiComposti(); // Numero totale di prodotti per il calcolo delle pagine
            int limit = 10; // Prodotti per pagina
            int totalePagine = (totale + limit - 1) / limit;
            
            // Corregge la pagina se fuori dai limiti
            if (pagina > totalePagine && totalePagine > 0) {
                pagina = totalePagine;
            }

            int offset = (pagina - 1) * limit;

            // Recupera solo i prodotti della pagina corrente
            List<ProdottoComposto> prodotti = dao.estraiProdottiCompostiPaginati(offset, limit);
            
            IWebExchange webExchange = webApp.buildExchange(request, response);
            WebContext ctx = new WebContext(webExchange, request.getLocale());
            ctx.setVariable("prodotti", prodotti);
            ctx.setVariable("paginaCorrente", pagina);
            ctx.setVariable("totalePagine", totalePagine);
            
            response.setContentType("text/html;charset=UTF-8");
            templateEngine.process("cliente_home", ctx, response.getWriter());
                   
        } catch (SQLException e) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel recupero dei prodotti");
        }
    }
}
