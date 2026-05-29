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

// Mappare il filter a tutti gli URL dell'applicazione
public class AccessControlFilter implements Filter {

    private static final Set<String> PUBLIC_PATHS = Set.of("/login", "/api/login", "/login.html");
    private static final String STATIC_PREFIX = "/static/";

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Eventuale inizializzazione (es. caricare whitelist URL pubblici)
    }

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

    @Override
    public void destroy() {
        // Pulizia risorse
    }
}
