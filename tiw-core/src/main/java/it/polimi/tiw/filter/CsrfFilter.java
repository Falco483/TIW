package it.polimi.tiw.filter;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Filtro CSRF basato sul Synchronizer Token Pattern.
 *
 * Sui GET inietta in sessione un token, sulle richieste mutanti
 * (POST/PUT/DELETE/PATCH) verifica che il token inviato dal client corrisponda
 * a quello in sessione. I path in EXCLUDED_PATHS e le risorse statiche sono
 * esentati dal controllo.
 */
public class CsrfFilter implements Filter {

    public static final String CSRF_TOKEN_SESSION_ATTR = "csrfToken";
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";
    public static final String CSRF_TOKEN_PARAM = "_csrf";

    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /**
     * Path esentati dal controllo CSRF. Il POST di login è l'unica richiesta
     * mutante che avviene prima che esista una sessione: senza questa eccezione
     * verrebbe bloccato con 403. Il login resta comunque protetto dalle
     * credenziali e non modifica dati di business.
     */
    private static final Set<String> EXCLUDED_PATHS = Set.of("/login", "/api/login");
    private static final String STATIC_PREFIX = "/static/";

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    /**
     * Previene gli attacchi CSRF (Cross-Site Request Forgery): inietta un token
     * sicuro in sessione sui GET e ne verifica la corrispondenza sulle richieste
     * mutanti (POST, PUT, DELETE, PATCH).
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @param chain il filter chain.
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // Bypass totale per risorse statiche
        String relativePath = httpReq.getRequestURI()
                .substring(httpReq.getContextPath().length());
        if (relativePath.startsWith(STATIC_PREFIX)) {
            chain.doFilter(request, response);
            return;
        }

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

    /**
     * Genera un CSRF token sicuro e casuale codificato in Base64 (URL-safe).
     *
     * @return una stringa casuale a 32 byte.
     */
    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Rifiuta la richiesta client inviando un errore 403 Forbidden.
     * Restituisce un JSON di errore se la richiesta è per le API (/api/*), altrimenti del testo in chiaro.
     *
     * @param req la servlet request.
     * @param resp la servlet response.
     * @param motivo il messaggio descrittivo del rifiuto.
     */
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
