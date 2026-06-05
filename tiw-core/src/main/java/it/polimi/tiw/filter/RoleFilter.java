package it.polimi.tiw.filter;

import java.io.IOException;

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
 * Filtro per il controllo dei ruoli utente (Autorizzazione).
 * Verifica che l'utente loggato stia accedendo a percorsi consentiti al suo ruolo.
 * Le risorse in "/fornitore/" e "/api/fornitore/" sono accessibili solo a FORNITORE.
 * Le risorse in "/cliente/" e "/api/cliente/" sono accessibili solo a CLIENTE.
 */
public class RoleFilter implements Filter {

    /**
     * Inizializzazione del filtro per i ruoli.
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    /**
     * Esegue l'autorizzazione basata sul ruolo per la richiesta corrente.
     * Confronta l'area di destinazione (Fornitore o Cliente) con il ruolo dell'utente
     * presente in sessione e blocca l'accesso non autorizzato.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @param chain il filter chain.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  req = (HttpServletRequest)  request;
        HttpServletResponse res = (HttpServletResponse) response;

        // Path relativo al context
        String relativePath = req.getRequestURI()
                .substring(req.getContextPath().length());

        // RoleFilter agisce solo su path protetti per ruolo
        boolean isFornitoreArea = relativePath.startsWith("/fornitore/")
                               || relativePath.startsWith("/api/fornitore/");
        boolean isClienteArea   = relativePath.startsWith("/cliente/")
                               || relativePath.startsWith("/api/cliente/");

        if (!isFornitoreArea && !isClienteArea) {
            // Path neutro (es: /login, /static/*) — nessun controllo ruolo
            chain.doFilter(request, response);
            return;
        }

        // AccessControlFilter ha già garantito che la sessione esista e contenga il DTO
        HttpSession session = req.getSession(false);
        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute(UtenteSessionDTO.SESSION_KEY);

        if (utente == null) {
            // Sessione presente ma DTO assente (es. sessione scaduta tra i due filtri)
            res.sendRedirect(req.getContextPath() + "/login");
            return;
        }

        boolean hasAccess = (isFornitoreArea && utente.isFornitore())
                         || (isClienteArea   && utente.isCliente());

        if (!hasAccess) {
            reject(req, res);
            return;
        }

        chain.doFilter(request, response);
    }

    /**
     * Rifiuta la richiesta inviando un errore 403 Forbidden.
     * Restituisce un JSON di errore se la richiesta è per le API (/api/*), altrimenti reindirizza al login.
     *
     * @param req la servlet request.
     * @param res la servlet response.
     */
    private void reject(HttpServletRequest req, HttpServletResponse res) throws IOException {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);

        if (req.getRequestURI().startsWith(req.getContextPath() + "/api/")) {
            res.setContentType("application/json");
            res.setCharacterEncoding("UTF-8");
            res.getWriter().write("{\"errore\": \"Accesso negato\"}");
        } else {
            res.sendRedirect(req.getContextPath() + "/login");
        }
    }

    /**
     * Rilascia le risorse allocate dal filtro.
     */
    @Override
    public void destroy() {}
}
