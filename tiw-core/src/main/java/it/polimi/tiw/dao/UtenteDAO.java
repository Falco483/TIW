package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.mindrot.jbcrypt.BCrypt;

import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.utils.UserRole;

/**
 * DAO per la gestione dell'autenticazione degli utenti.
 * Gestisce la verifica delle credenziali interfacciandosi con la tabella `utente`
 * e validando l'hash della password tramite BCrypt.
 */
public class UtenteDAO {

    private final Connection connection;

    /**
     * Costruttore del DAO.
     * @param connection La connessione al database.
     */
    public UtenteDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Verifica le credenziali di accesso (username e password).
     * Recupera l'utente dal DB, verifica che l'hash della password corrisponda
     * usando BCrypt e restituisce un DTO con i dati necessari per la sessione.
     *
     * @param username Lo username inserito dall'utente.
     * @param password La password in chiaro inserita dall'utente.
     * @return Un DTO contenente i dati dell'utente loggato, o null se fallisce.
     * @throws SQLException In caso di errori durante l'interrogazione del database.
     */
    public UtenteSessionDTO checkCredentials(String username, String password) throws SQLException {
        String query = """
                SELECT password_hash, nome, cognome, ruolo
                FROM utente
                WHERE username = ?
                """;

        try (PreparedStatement ps = connection.prepareStatement(query)) {
            ps.setString(1, username);

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null; // Utente non trovato
                }

                // Verifica la password hashata con BCrypt
                if (!BCrypt.checkpw(password, rs.getString("password_hash"))) {
                    return null; // Password errata
                }

                // Mappa il ruolo stringa all'enum corrispondente
                return new UtenteSessionDTO(
                        username,
                        rs.getString("nome"),
                        rs.getString("cognome"),
                        UserRole.fromString(rs.getString("ruolo"))
                );
            }
        }
    }
}
