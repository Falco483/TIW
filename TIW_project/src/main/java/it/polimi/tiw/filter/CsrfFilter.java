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
 * ============================================================================
 * CSRF FILTER — Synchronizer Token Pattern
 * ============================================================================
 *
 * COS'È UN ATTACCO CSRF (Cross-Site Request Forgery):
 *
 *   Immagina questa situazione:
 *   1. Mario è loggato su tiw-configuratore.polimi.it (ha un JSESSIONID cookie valido)
 *   2. Mario visita un sito malevolo: evil-site.com
 *   3. evil-site.com contiene un form nascosto:
 *        <form action="https://tiw-configuratore.polimi.it/api/configurazioni/42"
 *              method="POST">
 *          <input type="hidden" name="action" value="DELETE"/>
 *        </form>
 *        <script>document.forms[0].submit();</script>
 *   4. Il browser di Mario INVIA AUTOMATICAMENTE il cookie JSESSIONID con la
 *      richiesta POST (perché il cookie appartiene al dominio di destinazione)
 *   5. Il server vede un JSESSIONID valido → pensa che Mario stia facendo
 *      la richiesta legittimamente → CANCELLA la configurazione 42.
 *
 *   Mario non ha cliccato nulla. Evil-site ha "cavalcato" la sua sessione.
 *
 * PERCHÉ SUCCEDE:
 *   I cookie vengono inviati AUTOMATICAMENTE dal browser per ogni richiesta
 *   verso il dominio di appartenenza, indipendentemente da QUALE SITO ha
 *   generato la richiesta. L'autenticazione basata su cookie (HttpSession)
 *   è quindi intrinsecamente vulnerabile a CSRF.
 *
 * COME CI DIFENDIAMO — IL SYNCHRONIZER TOKEN PATTERN:
 *   1. Al primo accesso, generiamo un token casuale (32 byte, SecureRandom)
 *      e lo salviamo nella HttpSession dell'utente.
 *   2. Ogni form Thymeleaf include il token come <input type="hidden">.
 *      Ogni chiamata fetch() della SPA lo include come header HTTP custom.
 *   3. Ad ogni richiesta mutante (POST/PUT/DELETE), questo filtro verifica
 *      che il token ricevuto corrisponda a quello in sessione.
 *   4. Evil-site NON PUÒ leggere il token perché:
 *      - Non può accedere alla sessione del server
 *      - Non può leggere il DOM di un'altra origin (Same-Origin Policy)
 *      - Non può leggere gli header delle risposte cross-origin
 *
 *   Quindi evil-site non può forgiare una richiesta con il token corretto → bloccato.
 *
 * PERCHÉ MAPPIAMO SU "/*":
 *   Il filtro intercetta TUTTE le richieste. Sui GET non fa nulla (li lascia
 *   passare), ma inietta il token nella sessione se non c'è. Sui POST/PUT/DELETE
 *   verifica il token. Questa strategia è fail-safe: se aggiungi una nuova
 *   Servlet domani, è automaticamente protetta.
 * ============================================================================
 */
@WebFilter(filterName = "CsrfFilter", urlPatterns = "/*")
public class CsrfFilter implements Filter {

    /**
     * Nome dell'attributo di sessione dove salviamo il token.
     * Usato anche come nome del parametro del form Thymeleaf.
     */
    public static final String CSRF_TOKEN_SESSION_ATTR = "csrfToken";

    /**
     * Nome dell'header HTTP custom che la SPA JavaScript deve inviare.
     * Convenzione de-facto: "X-CSRF-Token" (usato da Rails, Django, ecc.)
     */
    public static final String CSRF_TOKEN_HEADER = "X-CSRF-Token";

    /**
     * Nome del parametro di form per le richieste Thymeleaf.
     * Il form conterrà: <input type="hidden" name="_csrf" th:value="${csrfToken}"/>
     */
    public static final String CSRF_TOKEN_PARAM = "_csrf";

    /**
     * Metodi HTTP che MODIFICANO lo stato del server.
     * GET e HEAD sono "safe methods" (RFC 7231) → non servono protezione CSRF
     * perché non devono causare side effects. Se le tue GET modificano dati,
     * hai un problema molto più grave del CSRF.
     */
    private static final Set<String> MUTATING_METHODS = Set.of("POST", "PUT", "DELETE", "PATCH");

    /**
     * Generatore di numeri casuali crittograficamente sicuro.
     * NON usare Math.random() o java.util.Random → sono predicibili.
     * SecureRandom usa l'entropia del sistema operativo (/dev/urandom su Linux,
     * CryptGenRandom su Windows).
     */
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest  httpReq  = (HttpServletRequest)  request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // -------------------------------------------------------------------
        // STEP 1: Assicurarsi che esista un token CSRF nella sessione.
        // Se l'utente non ha ancora una sessione, NON la creiamo qui
        // (la crea la LoginServlet). Se la sessione esiste ma non ha
        // il token, lo generiamo.
        // -------------------------------------------------------------------
        HttpSession session = httpReq.getSession(false);  // false = non creare

        if (session != null) {
            String token = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR);
            if (token == null) {
                token = generateToken();
                session.setAttribute(CSRF_TOKEN_SESSION_ATTR, token);
            }
            // Rendiamo il token accessibile ai template Thymeleaf via request attribute.
            // Thymeleaf legge ${csrfToken} dalla request, non dalla session.
            httpReq.setAttribute(CSRF_TOKEN_SESSION_ATTR, token);
        }

        // -------------------------------------------------------------------
        // STEP 2: Se il metodo è mutante (POST/PUT/DELETE), VERIFICARE il token.
        // -------------------------------------------------------------------
        if (MUTATING_METHODS.contains(httpReq.getMethod())) {

            // Senza sessione non puoi fare POST → 403 (non sei neanche loggato)
            if (session == null) {
                reject(httpReq, httpResp, "Sessione assente");
                return;
            }

            String sessionToken = (String) session.getAttribute(CSRF_TOKEN_SESSION_ATTR);
            if (sessionToken == null) {
                reject(httpReq, httpResp, "Token CSRF non presente in sessione");
                return;
            }

            // Cerchiamo il token dal client in DUE posti:
            //   1. Header HTTP "X-CSRF-Token" → usato dalla SPA (fetch API)
            //   2. Parametro form "_csrf"      → usato da Thymeleaf (form submit)
            String clientToken = httpReq.getHeader(CSRF_TOKEN_HEADER);
            if (clientToken == null || clientToken.isBlank()) {
                clientToken = httpReq.getParameter(CSRF_TOKEN_PARAM);
            }

            // Confronto constant-time per prevenire timing attacks.
            // String.equals() ritorna appena trova il primo char diverso →
            // un attaccante potrebbe misurare il tempo di risposta per
            // indovinare il token un carattere alla volta.
            // java.security.MessageDigest.isEqual() impiega sempre lo stesso tempo.
            if (clientToken == null || !java.security.MessageDigest.isEqual(
                    sessionToken.getBytes(), clientToken.getBytes())) {
                reject(httpReq, httpResp, "Token CSRF non valido");
                return;
            }
        }

        // -------------------------------------------------------------------
        // STEP 3: Token valido (o metodo safe) → lascia passare la richiesta.
        // -------------------------------------------------------------------
        chain.doFilter(request, response);
    }

    /**
     * Genera un token CSRF crittograficamente sicuro.
     * 32 byte di entropia → 256 bit → impossibile da indovinare per brute-force.
     * Codificato in Base64 URL-safe per poterlo inserire in header e form senza
     * problemi di encoding.
     */
    private String generateToken() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * Rifiuta la richiesta con 403 Forbidden.
     * Biforca la risposta in base al tipo di richiesta:
     *   - Se è una chiamata API (URL inizia con /api/) → risponde JSON
     *   - Se è un form submit Thymeleaf → risponde con error page
     */
    private void reject(HttpServletRequest req, HttpServletResponse resp, String motivo)
            throws IOException {
        resp.setStatus(HttpServletResponse.SC_FORBIDDEN);

        if (req.getRequestURI().startsWith(req.getContextPath() + "/api/")) {
            // Risposta JSON per la SPA
            resp.setContentType("application/json");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("{\"errore\": \"" + motivo + "\"}");
        } else {
            // Risposta testuale per i form Thymeleaf (o redirect a error page)
            resp.setContentType("text/plain");
            resp.setCharacterEncoding("UTF-8");
            resp.getWriter().write("403 Forbidden - " + motivo);
        }
    }
}
