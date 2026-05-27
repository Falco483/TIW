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
 * Servlet che serve la home della SPA iniettando il token CSRF.
 * Legge il file HTML statico e sostituisce "${csrfToken}" con il valore in sessione.
 */
@WebServlet("/home-fornitore")
public class HomeFornitoreServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        String csrfToken = (String) request.getSession().getAttribute("csrfToken");
        if (csrfToken == null) csrfToken = "";

        // Legge il file statico
        InputStream is = getServletContext().getResourceAsStream("/home-fornitore.html");
        if (is == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "File home-fornitore.html non trovato");
            return;
        }

        String html = new String(is.readAllBytes(), StandardCharsets.UTF_8);
        html = html.replace("${csrfToken}", csrfToken);

        response.setContentType("text/html;charset=UTF-8");
        response.getWriter().write(html);
    }
}
