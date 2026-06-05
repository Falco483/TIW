package it.polimi.tiw.servlet.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet che gestisce l'accesso alla home page dell'interfaccia Cliente (SPA).
 * Carica la pagina HTML statica "home-cliente.html" dal context e inietta dinamicamente
 * il token CSRF della sessione corrente all'interno del meta tag.
 */
@WebServlet("/home-cliente")
public class HomeClienteServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    /**
     * Gestisce la richiesta HTTP GET. Legge il file HTML, sostituisce il placeholder
     * col token CSRF e restituisce la pagina modificata al client.
     *
     * @param request la servlet request.
     * @param response la servlet response.
     * @throws ServletException in caso di errori della servlet.
     * @throws IOException in caso di errori di I/O nel caricamento del file.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String csrfToken = (String) request.getSession().getAttribute("csrfToken");
        if (csrfToken == null) csrfToken = "";

        //carica il file html statico
        InputStream is = getServletContext().getResourceAsStream("/home-cliente.html");
        if (is == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File home-cliente.html non trovato");
            return;
        }

        String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        html = html.replace("${csrfToken}", csrfToken);

        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(html);
    }
}
