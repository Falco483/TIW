package it.polimi.tiw.filter;

import jakarta.servlet.*;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

/**
 * CSRF Filter — Synchronizer Token Pattern.
 *
 * Intercetta TUTTE le richieste. Sui GET inietta il token in sessione.
 * Sui POST/PUT/DELETE verifica che il token del client corrisponda
 * a quello in sessione.
 *
 * ECCEZIONE CRITICA: i path in EXCLUDED_PATHS sono esentati dal
 * controllo CSRF perché l'utente non ha ancora una sessione (login)
 * o perché sono risorse statiche.
 */
@WebFilter(filterName = "CsrfFilter", urlPatterns = "/*")
public class CsrfFilter implements Filter {

    public static final String CSRF_TOKEN_SESSION_ATTR = "csrfToken";
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";
    public static final String CSRF_TOKEN_PARAM = "_csrf";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /**
     * Path esentati dal controllo CSRF (relativi al context path).
     *
     * PERCHÉ /login e /api/login:
     *   Il POST di login è l'unica richiesta mutante che avviene PRIMA
     *   che esista una sessione. Senza questa eccezione, il filtro
     *   bloccherebbe ogni tentativo di login con 403 "Sessione assente".
     *
     * SICUREZZA: il login è comunque protetto perché:
     *   1. Richiede credenziali valide (username + password)
     *   2. Non modifica dati di business (crea solo una sessione)
     *   3. Un attacco CSRF al login è inutile: l'attaccante loggerebbe
     *      la VITTIMA nel proprio account (Login CSRF), che è un attacco
     *      di basso impatto nel nostro dominio accademico.
     */
    private static final Set<String> EXCLUDED_PATHS = Set.of("/login", "/api/login");

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // STEP 1: Inietta token in sessione se esiste e non lo ha ancora.
        HttpSession session = httpReq.getSession(false);

        if (session != null) {
            String token = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR);
            if (token == null) {
                token = generateToken();
                session.setAttribute(CSRF_TOKEN_SESSION_ATTR, token);
            }
            httpReq.setAttribute(CSRF_TOKEN_SESSION_ATTR, token);
        }

        // STEP 2: Verifica CSRF solo su metodi mutanti E path non esclusi.
        if (MUTATING_METHODS.contains(httpReq.getMethod())) {

            // Calcola il path relativo al context (es: /tiw-ssr/login → /login)
            String relativePath = httpReq.getRequestURI()
                    .substring(httpReq.getContextPath().length());

            // Bypass per i path esclusi (login)
            if (EXCLUDED_PATHS.contains(relativePath)) {
                chain.doFilter(request, response);
                return;
            }

            // Senza sessione → 403
            if (session == null) {
                reject(httpReq, httpResp, "Sessione assente");
                return;
            }

            String sessionToken = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR);
            if (sessionToken == null) {
                reject(httpReq, httpResp, "Token CSRF non presente in sessione");
                return;
            }

            // Cerca token dal client: header (SPA) o parametro form (Thymeleaf)
            String clientToken = httpReq.getHeader(CSRF_TOKEN_HEADER);
            if (clientToken == null || clientToken.isBlank()) {
                clientToken = httpReq.getParameter(CSRF_TOKEN_PARAM);
            }

            // Confronto constant-time contro timing attacks
            if (clientToken == null || !java.security.MessageDigest.isEqual(
                    sessionToken.getBytes(), clientToken.getBytes())) {
                reject(httpReq, httpResp, "Token CSRF non valido");
                return;
            }
        }

        // STEP 3: Richiesta valida → procedi
        chain.doFilter(request, response);
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private void reject(HttpServletRequest req, HttpServletResponse resp, String motivo)
            throws IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);

        if (req.getRequestURI().startsWith(req.getContextPath() + "/api/")) {
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"errore\": \"" + motivo + "\"}");
        } else {
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("403 Forbidden - " + motivo);
        }
    }
}
