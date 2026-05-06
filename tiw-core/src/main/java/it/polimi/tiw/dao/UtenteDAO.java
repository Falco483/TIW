package it.polimi.tiw.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.mindrot.jbcrypt.BCrypt;

import it.polimi.tiw.dto.UtenteSessionDTO;
import it.polimi.tiw.utils.UserRole;

/**
 * UtenteDAO — Data Access Object per la tabella `utente`.
 * Riceve Connection via costruttore (DI manuale). NON la chiude.
 */
public class UtenteDAO {

    private final Connection connection;

    public UtenteDAO(Connection connection) {
        this.connection = connection;
    }

    /**
     * Verifica le credenziali di accesso.
     *
     * @return il DTO da salvare in sessione, oppure {@code null} se username
     *         non esiste o la password non corrisponde
     * @throws SQLException in caso di errore DB
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
                    return null; // utente non trovato
                }

                if (!BCrypt.checkpw(password, rs.getString("password_hash"))) {
                    return null; // password errata
                }

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
