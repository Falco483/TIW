package it.polimi.tiw.dto;

import it.polimi.tiw.utils.UserRole;

/**
 * DTO immutabile con i dati dell'utente salvati in sessione.
 *
 * Usiamo questo DTO al posto del POJO Utente per non tenere l'hash della
 * password in sessione (vivrebbe in RAM per tutta la sua durata). Contiene
 * solo i campi che servono davvero: username per le query, nome e cognome per
 * la home, ruolo per i controlli di autorizzazione.
 *
 * Essendo un record è immutabile: il ruolo non può essere riscritto a runtime
 * per scalare i privilegi. Tenere nome e cognome qui evita inoltre una query
 * extra a ogni caricamento della home.
 */
public record UtenteSessionDTO(
        String username,
        String nome,
        String cognome,
        UserRole ruolo
) {
    /** Chiave con cui il DTO è salvato in sessione, centralizzata per evitare stringhe sparse. */
    public static final String SESSION_KEY = "utente";

    public boolean isFornitore() {
        return ruolo == UserRole.FORNITORE;
    }

    public boolean isCliente() {
        return ruolo == UserRole.CLIENTE;
    }
}
