package it.polimi.tiw.filter;

import java.io.IOException;
import java.util.Set;

import it.polimi.tiw.dto.UtenteSessionDTO;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Filtro di Controllo degli Accessi.
 * Intercetta tutte le richieste dell'applicazione e verifica se l'utente è autenticato.
 * Permette il libero passaggio solo per le risorse statiche e i path pubblici (login).
 */
public class AccessControlFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/login", "/api/login", "/login.html");
    private static final String STATIC_PREFIX = "/static/";

    /**
     * Inizializzazione del filtro.
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Inizializzazione vuota
    }

    /**
     * Esegue il filtraggio della richiesta. Se l'utente non è autenticato e cerca di accedere
     * a una risorsa privata, viene reindirizzato al login o rifiutato con un codice 401 Unauthorized.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @param chain il filter chain.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        // Cast a HttpServlet* per accedere alla sessione
        HttpServletRequest  req  = (HttpServletRequest)  request;
        HttpServletResponse res  = (HttpServletResponse) response;

        HttpSession session = req.getSession(false);

        // Verificare se l'utente è autenticato
        boolean isAuthenticated = (session != null
                                  && session.getAttribute(UtenteSessionDTO.SESSION_KEY) 
                                  instanceof UtenteSessionDTO);

        // Controllare se la richiesta è verso una risorsa pubblica
        String relativePath = req.getRequestURI().substring(req.getContextPath().length());
        boolean isPublic = PUBLIC_PATHS.contains(relativePath)
                        || relativePath.startsWith(STATIC_PREFIX)
                        || relativePath.endsWith("/index.html");

        if (isAuthenticated || isPublic) {
            chain.doFilter(request, response);
        } else if (relativePath.startsWith("/api/")) {
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"errore\": \"Non autenticato\"}");
        } else {
            res.sendRedirect(req.getContextPath() + "/login");
        }
    }

    /**
     * Distrugge il filtro rilasciando eventuali risorse allocate.
     */
    @Override
    public void destroy() {
        // Pulizia risorse
    }
}
