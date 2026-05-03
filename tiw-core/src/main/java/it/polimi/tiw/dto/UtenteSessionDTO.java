package it.polimi.tiw.dto;

/**
 * DTO immutabile per i dati utente salvati in HttpSession.
 *
 * PERCHÉ UN RECORD E NON IL POJO Utente:
 *
 *   Il POJO Utente ha il campo password_hash. Se lo salvi in sessione,
 *   l'hash della password vive in RAM per tutta la durata della sessione.
 *   È un rischio inutile: se un attaccante riesce a dumpare la RAM del
 *   processo Tomcat (heap dump), trova tutti gli hash delle sessioni attive.
 *
 *   Questo DTO contiene SOLO i 4 campi necessari alla UI:
 *   - username: per identificare l'utente nelle query (WHERE cliente_username = ?)
 *   - nome/cognome: per il saluto "Ciao, Mario Rossi" nella home page
 *   - ruolo: per i controlli di autorizzazione nel Filter e nei Controller
 *
 *   ~200 byte in RAM per sessione. Con 1000 sessioni simultanee = 200 KB.
 *   Trascurabile anche su un Tomcat con 256 MB di heap.
 *
 * PERCHÉ UN RECORD JAVA:
 *   Un record è immutabile per design: i campi sono final, il costruttore
 *   è auto-generato, equals/hashCode/toString sono auto-generati.
 *   Non puoi fare session.getAttribute("utente").setRuolo("FORNITORE")
 *   per scalare i privilegi → protezione strutturale.
 *
 * PERCHÉ NON SALVIAMO SOLO userId + ruolo:
 *   La specifica (SPECIFICHE_PROGETTO.md, riga 284) dice:
 *   "HOME page mostra nome e cognome dell'utente"
 *   Se salvi solo userId, ogni caricamento della home richiede:
 *     SELECT nome, cognome FROM utente WHERE username = ?
 *   Con il DTO in sessione, zero query aggiuntive.
 */
public record UtenteSessionDTO(
        String username,
        String nome,
        String cognome,
        String ruolo      // "FORNITORE" o "CLIENTE"
) {
    /**
     * Chiave di sessione. Usata da Filter e Controller per recuperare il DTO.
     * Centralizzata qui per evitare stringhe magiche sparse nel codice.
     *
     * Uso: session.getAttribute(UtenteSessionDTO.SESSION_KEY)
     */
    public static final String SESSION_KEY = "utente";

    public boolean isFornitore() {
        return "FORNITORE".equals(ruolo);
    }

    public boolean isCliente() {
        return "CLIENTE".equals(ruolo);
    }
}
