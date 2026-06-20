package it.polimi.tiw.utils;

import jakarta.servlet.http.Part;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * Gestisce il salvataggio delle fotografie delle SKU in una directory esterna
 * condivisa da entrambe le versioni dell'applicazione (HTML e SPA).
 *
 * I file vengono scritti in {@code $CATALINA_BASE/tiw-foto}: una posizione
 * ricavata a runtime (quindi portabile, senza path hardcodati) ed esterna ai
 * WAR, così che gli upload sopravvivano ai redeploy e siano serviti a entrambe
 * le app dallo stesso context Tomcat montato su {@code /foto}.
 *
 * Nel database non si salva mai il path su disco, ma il valore relativo
 * {@code foto/<nomefile>}; la view lo trasforma nell'URL {@code /foto/<nomefile>}.
 */
public final class FotoStorage {

    /** Nome della cartella esterna, relativa a CATALINA_BASE. */
    private static final String DIR_NAME = "tiw-foto";

    private FotoStorage() {}

    /**
     * Restituisce la directory esterna in cui sono salvate le fotografie,
     * creandola se non esiste.
     *
     * @return la cartella {@code $CATALINA_BASE/tiw-foto}.
     */
    public static File directory() {
        File dir = new File(System.getProperty("catalina.base"), DIR_NAME);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    /**
     * Salva su disco la foto caricata e restituisce il valore da memorizzare nel
     * database. Il nome del file è reso univoco con un UUID per evitare collisioni.
     *
     * @param filePart la parte multipart contenente l'immagine.
     * @return il path relativo da salvare nel DB, nella forma {@code foto/<nomefile>}.
     * @throws IOException se la scrittura del file fallisce.
     */
    public static String salva(Part filePart) throws IOException {
        String nomeOriginale = Paths.get(filePart.getSubmittedFileName()).getFileName().toString();
        String nomeFile = UUID.randomUUID() + "_" + nomeOriginale;

        File destinazione = new File(directory(), nomeFile);
        try (InputStream input = filePart.getInputStream()) {
            Files.copy(input, destinazione.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        return "foto/" + nomeFile;
    }
}
