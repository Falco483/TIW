package it.polimi.tiw.servlet.api;

import it.polimi.tiw.dao.SKUDAO;
import it.polimi.tiw.model.SKU;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import jakarta.servlet.annotation.MultipartConfig;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.math.BigDecimal;

/**
 * Controller API per la gestione delle SKU (JSON e Multipart).
 */
@WebServlet("/api/sku")
@MultipartConfig(fileSizeThreshold = 1024 * 1024, maxFileSize = 1024 * 1024 * 10, maxRequestSize = 1024 * 1024 * 50)
public class ApiSkuController extends HttpServlet {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private Connection connection = null;

    /**
     * Inizializza la servlet e ottiene la connessione al database tramite la ConnectionFactory.
     * @throws jakarta.servlet.ServletException se la connessione al database fallisce.
     */
    @Override
    public void init() throws jakarta.servlet.ServletException {
        try {
            connection = it.polimi.tiw.utils.ConnectionFactory.getConnection(getServletContext());
        } catch (SQLException | ClassNotFoundException e) {
            throw new jakarta.servlet.UnavailableException("Connessione al DB fallita");
        }
    }

    /**
     * Termina il ciclo di vita della servlet chiudendo in modo sicuro la connessione al database.
     */
    @Override
    public void destroy() {
        try {
            if (connection != null && !connection.isClosed())
                connection.close();
        } catch (SQLException e) {}
    }

    /**
     * Gestisce la creazione di una nuova SKU, permettendo l'upload opzionale di un'immagine.
     * I dati arrivano nel formato multipart/form-data. L'immagine viene salvata nella cartella 'uploads/'.
     * @param request La richiesta HTTP POST.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore durante l'upload del file o la scrittura della risposta.
     */
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        Connection conn = this.connection;
        if (conn == null) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Connessione al DB non disponibile");
            return;
        }

        try {
            String codiceStr = request.getParameter("codice");
            String nome = request.getParameter("nome");
            String descrizioneTecnica = request.getParameter("descrizioneTecnica");
            String prezzoStr = request.getParameter("prezzo");

            if (codiceStr == null || nome == null || prezzoStr == null) {
                sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Dati mancanti");
                return;
            }

            SKU nuovaSku = new SKU();
            nuovaSku.setCodice(Integer.parseInt(codiceStr));
            nuovaSku.setNome(nome);
            nuovaSku.setDescrizioneTecnica(descrizioneTecnica);
            nuovaSku.setPrezzo(new BigDecimal(prezzoStr));

            // Gestione Upload File
            String fotografiaUrl = "";
            Part filePart = request.getPart("fotografia_file");
            if (filePart != null && filePart.getSize() > 0) {
                String fileName = UUID.randomUUID().toString() + "_" + Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
                String uploadPath = getServletContext().getRealPath("") + File.separator + "uploads";
                File uploadDir = new File(uploadPath);
                if (!uploadDir.exists()) uploadDir.mkdir();
                
                File file = new File(uploadPath + File.separator + fileName);
                try (InputStream input = filePart.getInputStream()) {
                    Files.copy(input, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                fotografiaUrl = "uploads/" + fileName;
            }
            nuovaSku.setFotografia(fotografiaUrl);
            
            SKUDAO dao = new SKUDAO(conn);
            SKU skuCreata = dao.insert(nuovaSku);
            
            response.setStatus(HttpServletResponse.SC_CREATED);
            MAPPER.writeValue(response.getOutputStream(), Map.of("data", skuCreata));
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel salvataggio della SKU nel database");
        } catch (Exception e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Dati di input malformati o non validi");
        }
    }

    /**
     * Recupera la lista di tutte le SKU presenti a catalogo e le restituisce in formato JSON.
     * @param request La richiesta HTTP GET.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore durante la serializzazione JSON.
     */
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            SKUDAO dao = new SKUDAO(connection);
            List<SKU> skus = dao.findAll();
            MAPPER.writeValue(response.getOutputStream(), Map.of("data", skus));
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nel recupero delle SKU");
        }
    }

    /**
     * Aggiorna una SKU esistente. Supporta due formati di richiesta:
     * - `application/json` per gli aggiornamenti base (ad esempio le modifiche "inline" del testo).
     * - `multipart/form-data` per l'aggiornamento che include anche una nuova fotografia.
     * @param request La richiesta HTTP PUT.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore durante il salvataggio del file o la manipolazione dei dati.
     */
    @Override
    protected void doPut(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        try {
            SKU sku = null;
            // Supporta sia application/json (per update veloci) sia multipart
            if (request.getContentType() != null && request.getContentType().startsWith("multipart/form-data")) {
                sku = new SKU();
                sku.setId(Integer.parseInt(request.getParameter("id")));
                sku.setCodice(Integer.parseInt(request.getParameter("codice")));
                sku.setNome(request.getParameter("nome"));
                sku.setDescrizioneTecnica(request.getParameter("descrizioneTecnica"));
                sku.setPrezzo(new BigDecimal(request.getParameter("prezzo")));
                sku.setFotografia(request.getParameter("fotografiaUrlOriginale")); // Default al vecchio

                Part filePart = request.getPart("fotografia_file");
                if (filePart != null && filePart.getSize() > 0) {
                    String fileName = UUID.randomUUID().toString() + "_" + Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
                    String uploadPath = getServletContext().getRealPath("") + File.separator + "uploads";
                    File uploadDir = new File(uploadPath);
                    if (!uploadDir.exists()) uploadDir.mkdir();
                    File file = new File(uploadPath + File.separator + fileName);
                    try (InputStream input = filePart.getInputStream()) {
                        Files.copy(input, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    sku.setFotografia("uploads/" + fileName);
                }
            } else {
                sku = MAPPER.readValue(request.getInputStream(), SKU.class);
            }

            SKUDAO dao = new SKUDAO(connection);
            dao.update(sku);
            SKU updated = dao.findById(sku.getId());
            MAPPER.writeValue(response.getOutputStream(), Map.of("data", updated));
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nell'aggiornamento della SKU");
        } catch (Exception e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST,
                    "Dati di input malformati o non validi");
        }
    }

    /**
     * Elimina in modo definitivo una SKU dal database e tutte le sue associazioni ai prodotti semplici.
     * L'operazione è distruttiva ed è protetta da un controllo logico in `SKUDAO`.
     * @param request La richiesta HTTP DELETE contenente il parametro `id`.
     * @param response La risposta HTTP.
     * @throws IOException Se si verifica un errore nella restituzione dell'esito.
     */
    @Override
    protected void doDelete(HttpServletRequest request, HttpServletResponse response)
            throws IOException {

        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String idParam = request.getParameter("id");
        if (idParam == null || idParam.isBlank()) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "Parametro 'id' mancante");
            return;
        }

        try {
            int id = Integer.parseInt(idParam);
            SKUDAO dao = new SKUDAO(connection);

            dao.eliminaDefinitivamente(id);
            MAPPER.writeValue(response.getOutputStream(), Map.of("success", true));
        } catch (NumberFormatException e) {
            sendError(response, HttpServletResponse.SC_BAD_REQUEST, "ID non valido");
        } catch (SQLException e) {
            sendError(response, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Errore nell'eliminazione della SKU: " + e.getMessage());
        }
    }

    /**
     * Invia un messaggio di errore strutturato al client in formato JSON.
     * @param response L'oggetto HttpServletResponse per l'invio.
     * @param status Il codice di stato HTTP d'errore (es. 400, 500).
     * @param messaggio Il dettaglio dell'errore.
     * @throws IOException Se si verifica un problema di comunicazione col client.
     */
    private void sendError(HttpServletResponse response, int status, String messaggio)
            throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        MAPPER.writeValue(response.getOutputStream(), Map.of("errore", messaggio));
    }
}

