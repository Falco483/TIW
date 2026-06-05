package it.polimi.tiw.servlet.web;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.WebApplicationTemplateResolver;
import org.thymeleaf.web.IWebExchange;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;

/**
 * Servlet che mostra il risultato del salvataggio di un elemento (SKU o Prodotto)
 * creato dal fornitore, implementando il pattern PRG (Post/Redirect/Get) per prevenire
 * il reinvio del form in caso di refresh della pagina.
 */
@WebServlet("/fornitore/risultato")
public class RisultatoFornitoreServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    private JakartaServletWebApplication webApp;
    private TemplateEngine templateEngine;

    private static final String SESSION_RISULTATO      = "home.risultato";
    private static final String SESSION_TIPO_RISULTATO = "home.tipoRisultato";

    /**
     * Inizializza il motore Thymeleaf e configura i template resolver.
     */
    @Override
    public void init() throws ServletException {
        webApp = JakartaServletWebApplication.buildApplication(getServletContext());
        WebApplicationTemplateResolver resolver = new WebApplicationTemplateResolver(webApp);
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setPrefix("/WEB-INF/templates/");
        resolver.setSuffix(".html");
        templateEngine = new TemplateEngine();
        templateEngine.setTemplateResolver(resolver);
    }

    /**
     * Gestisce la richiesta HTTP GET. Legge il risultato del form inserito nella sessione
     * (e lo rimuove immediatamente per pulizia), effettuando poi il rendering del template.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errore.
     * @throws IOException in caso di errore di I/O.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        HttpSession session = request.getSession(false);
        Object risultato = null;
        String tipoRisultato = null;

        if (session != null) {
            risultato     = session.getAttribute(SESSION_RISULTATO);
            tipoRisultato = (String) session.getAttribute(SESSION_TIPO_RISULTATO);

            session.removeAttribute(SESSION_RISULTATO);
            session.removeAttribute(SESSION_TIPO_RISULTATO);
        }

        if (risultato == null) {
            // Se non c'è un risultato da mostrare, reindirizza alla home
            response.sendRedirect(request.getContextPath() + "/fornitore/home");
            return;
        }

        IWebExchange webExchange = webApp.buildExchange(request, response);
        WebContext ctx = new WebContext(webExchange, request.getLocale());
        ctx.setVariable("risultato", risultato);
        ctx.setVariable("tipoRisultato", tipoRisultato);

        response.setContentType("text/html;charset=UTF-8");
        templateEngine.process("fornitore/risultato", ctx, response.getWriter());
    }
}
