package it.polimi.tiw.utils;

import jakarta.servlet.ServletContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class ConnectionFactory {

    private ConnectionFactory() {}

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
