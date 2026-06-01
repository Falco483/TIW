package it.polimi.tiw.servlet.web;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@WebServlet("/home-cliente")
public class HomeClienteServlet extends HttpServlet{
    private static final long serialVersionUID = 1L;

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
