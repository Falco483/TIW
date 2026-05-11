package it.polimi.tiw.utils;

import jakarta.servlet.ServletContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/**
 * Factory per la creazione di connessioni JDBC al database MySQL.
 * Legge i parametri di configurazione (driver, URL, credenziali) dal web.xml tramite il ServletContext.
 */
public class ConnectionFactory {

    private ConnectionFactory() {}

    /**
     * Stabilisce e restituisce una nuova connessione al database.
     * @param ctx Il ServletContext da cui leggere i parametri d'inizializzazione.
     * @return Una connessione JDBC attiva.
     * @throws SQLException In caso di errore di connessione.
     * @throws ClassNotFoundException Se il driver JDBC non viene trovato.
     */
    public static Connection getConnection(ServletContext ctx)
            throws SQLException, ClassNotFoundException {

        String driver   = ctx.getInitParameter("dbDriver");
        String url      = ctx.getInitParameter("dbUrl");
        String user     = ctx.getInitParameter("dbUser");
        String password = ctx.getInitParameter("dbPassword");

        Class.forName(driver);
        return DriverManager.getConnection(url, user, password);
    }
}
