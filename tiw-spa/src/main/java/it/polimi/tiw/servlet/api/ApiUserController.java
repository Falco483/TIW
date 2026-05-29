package it.polimi.tiw.servlet.api;

import it.polimi.tiw.dto.UtenteSessionDTO;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Map;

/**
 * Controller API per ottenere le informazioni dell'utente autenticato.
 */
@WebServlet("/api/me")
public class ApiUserController extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * Recupera i dati dell'utente attualmente autenticato in sessione, assieme al token CSRF.
     * Se la sessione o l'utente non sono validi, restituisce un errore 401 Unauthorized.
     * @param request La richiesta HTTP GET contenente la sessione corrente.
     * @param response La risposta HTTP per restituire le info dell'utente in formato JSON.
     * @throws IOException Se si verifica un errore durante la serializzazione JSON.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        HttpSession session = request.getSession(false);
        if (session == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            MAPPER.writeValue(response.getOutputStream(), Map.of(
                    "success", false,
                    "error", "Sessione non valida"
            ));
            return;
        }

        UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute(UtenteSessionDTO.SESSION_KEY);
        if (utente == null) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            MAPPER.writeValue(response.getOutputStream(), Map.of(
                    "success", false,
                    "error", "Utente non autenticato"
            ));
            return;
        }

        String csrfToken = (String) session.getAttribute(it.polimi.tiw.filter.CsrfFilter.CSRF_TOKEN_SESSION_ATTR);

        MAPPER.writeValue(response.getOutputStream(), Map.of(
                "username", utente.username(),
                "nome", utente.nome(),
                "cognome", utente.cognome(),
                "ruolo", utente.ruolo().name(),
                "csrfToken", csrfToken != null ? csrfToken : ""
        ));
    }
}
