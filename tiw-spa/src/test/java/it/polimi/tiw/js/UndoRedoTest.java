package it.polimi.tiw.js;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test dell'architettura Undo/Redo della SPA fornitore.
 *
 * I moduli JS REALI ({@code tree-model.js} e {@code history-manager.js}) vengono
 * caricati in un motore GraalVM JS; un harness ({@code editor-sim.js}) riproduce
 * l'orchestrazione dell'editor (snapshot/commit/undo/redo) così come prevista dal
 * piano di implementazione. I test simulano sessioni di editing realistiche
 * (più operazioni consecutive) e verificano che il modello dopo undo/redo sia
 * esattamente quello atteso, coprendo anche gli edge case delle specifiche.
 */
class UndoRedoTest {

    private Context context;
    private Value sim;

    // Percorsi relativi alla directory del modulo (tiw-spa), dir di lavoro di Surefire.
    private static final Path JS_DIR = Paths.get("src/main/webapp/js");
    private static final Path HARNESS = Paths.get("src/test/resources/js/editor-sim.js");

    @BeforeEach
    void setUp() throws IOException {
        context = Context.newBuilder("js").allowAllAccess(true).build();
        loadModule(JS_DIR.resolve("tree-model.js"), "treeModel");
        loadModule(JS_DIR.resolve("history-manager.js"), "history");
        loadModule(HARNESS, "editorSim");
        sim = context.eval("js", "editorSim");
    }

    @AfterEach
    void tearDown() {
        if (context != null) context.close();
    }

    /**
     * Carica un file JS che dichiara {@code const <name>} al top-level e ne espone il
     * binding su globalThis, così da renderlo visibile ai moduli caricati dopo.
     */
    private void loadModule(Path path, String name) throws IOException {
        String code = Files.readString(path) + "\n; globalThis." + name + " = " + name + ";";
        context.eval(Source.newBuilder("js", code, path.getFileName().toString()).build());
    }

    // --- Helper per costruire JSON dei nodi ---------------------------------

    private String composto(String id, String codice, String nome) {
        return "{\"id\":\"" + id + "\",\"tipo\":\"COMPOSTO\",\"codice\":\"" + codice
                + "\",\"nome\":\"" + nome + "\",\"prezzoMin\":0,\"prezzoMax\":0,\"figli\":[]}";
    }

    private String semplice(String id, String codice, String nome) {
        return "{\"id\":\"" + id + "\",\"tipo\":\"SEMPLICE\",\"codice\":\"" + codice
                + "\",\"nome\":\"" + nome + "\",\"skus\":[]}";
    }

    private String sku(String id, String codice, String nome, String prezzo) {
        return "{\"id\":\"" + id + "\",\"codice\":\"" + codice + "\",\"nome\":\"" + nome
                + "\",\"prezzo\":\"" + prezzo + "\"}";
    }

    // --- Scorciatoie verso l'harness ----------------------------------------

    private boolean exec(String op, Object... args) { return sim.invokeMember(op, args).asBoolean(); }
    private boolean undo() { return sim.invokeMember("undo").asBoolean(); }
    private boolean redo() { return sim.invokeMember("redo").asBoolean(); }
    private boolean canUndo() { return sim.invokeMember("canUndo").asBoolean(); }
    private boolean canRedo() { return sim.invokeMember("canRedo").asBoolean(); }
    private int childCount(String id) { return sim.invokeMember("childCount", id).asInt(); }
    private int skuCount(String id) { return sim.invokeMember("skuCount", id).asInt(); }
    private boolean hasNode(String id) { return sim.invokeMember("hasNode", id).asBoolean(); }
    private boolean hasSku(String p, String s) { return sim.invokeMember("hasSku", p, s).asBoolean(); }
    private String nodeField(String id, String f) { return sim.invokeMember("nodeField", id, f).asString(); }
    private int childIndexOf(String p, String c) { return sim.invokeMember("childIndexOf", p, c).asInt(); }
    private int pendingCount() { return sim.invokeMember("pendingCount").asInt(); }
    private int skuAssociateCount() { return sim.invokeMember("skuAssociateCount").asInt(); }
    private String treeJSON() { return sim.invokeMember("treeJSON").asString(); }

    // ========================================================================
    // 1. Operazione singola: undo e redo
    // ========================================================================

    @Test
    @DisplayName("Aggiunta sottoprodotto: undo lo rimuove, redo lo ripristina")
    void undoRedoSingolaOperazione() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        assertFalse(canUndo(), "all'avvio non c'è nulla da annullare (R14)");

        exec("addChild", "root", semplice("c1", "2000", "CPU"));
        assertEquals(1, childCount("root"));
        assertTrue(canUndo());
        assertFalse(canRedo());

        assertTrue(undo());
        assertEquals(0, childCount("root"), "undo rimuove il sottoprodotto");
        assertFalse(hasNode("c1"));
        assertTrue(canRedo());

        assertTrue(redo());
        assertEquals(1, childCount("root"), "redo ripristina il sottoprodotto");
        assertTrue(hasNode("c1"));
    }

    // ========================================================================
    // 2. Sessione realistica: più operazioni, poi più undo e più redo
    // ========================================================================

    @Test
    @DisplayName("Sessione realistica: 4 operazioni, 4 undo allo stato iniziale, 4 redo")
    void sessioneRealisticaMultiUndoRedo() {
        sim.invokeMember("start", composto("root", "1000", "Computer"));
        String statoIniziale = treeJSON();

        // L'utente lavora: due sottoprodotti, una SKU, una modifica attributo
        exec("addChild", "root", composto("gruppo", "2000", "Gruppo"));   // op1
        exec("addChild", "gruppo", semplice("cpu", "3000", "CPU"));        // op2
        exec("addSku", "cpu", sku("s1", "0001", "Intel i7", "300"));       // op3
        exec("editNode", "root", "nome", "Computer Gaming");               // op4

        String statoFinale = treeJSON();
        assertEquals(4, pendingCount());
        assertEquals(1, skuCount("cpu"));
        assertEquals("Computer Gaming", nodeField("root", "nome"));

        // 4 undo → ritorno esatto allo stato iniziale, in ordine inverso
        assertTrue(undo()); // annulla edit nome
        assertEquals("Computer", nodeField("root", "nome"));
        assertTrue(undo()); // annulla SKU
        assertEquals(0, skuCount("cpu"));
        assertTrue(undo()); // annulla CPU
        assertFalse(hasNode("cpu"));
        assertTrue(undo()); // annulla gruppo
        assertFalse(hasNode("gruppo"));

        assertEquals(statoIniziale, treeJSON(), "dopo N undo si torna allo stato iniziale");
        assertFalse(canUndo());

        // 4 redo → ritorno esatto allo stato finale
        assertTrue(redo());
        assertTrue(redo());
        assertTrue(redo());
        assertTrue(redo());
        assertEquals(statoFinale, treeJSON(), "dopo N redo si torna allo stato finale");
        assertFalse(canRedo());
    }

    @Test
    @DisplayName("Undo parziale poi redo parziale ritorna allo stato corretto")
    void undoRedoParziale() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("addChild", "root", semplice("a", "2000", "A"));
        exec("addChild", "root", semplice("b", "3000", "B"));
        exec("addChild", "root", semplice("c", "4000", "C"));
        assertEquals(3, childCount("root"));

        // 2 undo: resta solo "a"
        undo();
        undo();
        assertEquals(1, childCount("root"));
        assertTrue(hasNode("a"));
        assertFalse(hasNode("b"));

        // 1 redo: torna "b"
        redo();
        assertEquals(2, childCount("root"));
        assertTrue(hasNode("b"));
        assertFalse(hasNode("c"));
    }

    // ========================================================================
    // 3. Edge case: nuova operazione dopo undo svuota la pila redo (R2/R13)
    // ========================================================================

    @Test
    @DisplayName("Nuova operazione dopo un undo invalida il redo (R2/R13)")
    void nuovaOperazioneSvuotaRedo() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("addChild", "root", semplice("a", "2000", "A"));
        exec("addChild", "root", semplice("b", "3000", "B"));

        undo();                  // annulla "b" → redo disponibile
        assertTrue(canRedo());

        exec("addChild", "root", semplice("c", "4000", "C")); // nuova operazione
        assertFalse(canRedo(), "la nuova operazione deve svuotare la pila redo");
        assertTrue(hasNode("c"));
        assertFalse(hasNode("b"), "il ramo 'b' annullato non è più ripristinabile");
        assertEquals(2, childCount("root"));
    }

    // ========================================================================
    // 4. Edge case: undo/redo su pila vuota (EC1, EC5, R6, R9)
    // ========================================================================

    @Test
    @DisplayName("Undo/redo a pila vuota non hanno effetto")
    void undoRedoPilaVuota() {
        sim.invokeMember("start", composto("root", "1000", "PC"));

        assertFalse(undo(), "undo a vuoto = nessun effetto (R6)");
        assertFalse(redo(), "redo a vuoto = nessun effetto (R9)");

        exec("addChild", "root", semplice("a", "2000", "A"));
        undo();
        assertFalse(undo(), "ulteriori undo oltre lo stato iniziale non hanno effetto (EC5)");
        assertEquals(0, childCount("root"));

        redo();
        assertFalse(redo(), "redo oltre l'ultimo stato non ha effetto");
        assertEquals(1, childCount("root"));
    }

    // ========================================================================
    // 5. Edge case: modifica identica non genera checkpoint (R3, EC3)
    // ========================================================================

    @Test
    @DisplayName("Reinserire lo stesso valore di un attributo non crea un checkpoint")
    void modificaIdenticaNoOp() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("editNode", "root", "nome", "Workstation");
        assertTrue(canUndo());

        // Stesso identico valore → no-op
        boolean committed = exec("editNode", "root", "nome", "Workstation");
        assertFalse(committed, "nessun checkpoint per una modifica identica (R3/EC3)");

        // Un solo undo riporta al nome originale
        undo();
        assertEquals("PC", nodeField("root", "nome"));
        assertFalse(canUndo());
    }

    @Test
    @DisplayName("Editing di più attributi è annullabile come unità singole distinte")
    void editAttributiUnitaSingole() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("editNode", "root", "nome", "Server");      // unità 1
        exec("editNode", "root", "prezzoMin", "500");    // unità 2

        assertEquals("Server", nodeField("root", "nome"));
        assertEquals("500", nodeField("root", "prezzoMin"));

        undo(); // annulla solo prezzoMin
        assertEquals("0", nodeField("root", "prezzoMin"));
        assertEquals("Server", nodeField("root", "nome"), "il nome resta invariato");

        undo(); // annulla nome
        assertEquals("PC", nodeField("root", "nome"));
    }

    // ========================================================================
    // 6. Edge case: cancellazione associazioni e ripristino posizione (E1, E2)
    // ========================================================================

    @Test
    @DisplayName("Undo dello scollegamento di un figlio ne ripristina la posizione (E1)")
    void unlinkNodeRipristinaPosizione() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("addChild", "root", semplice("a", "2000", "A"));
        exec("addChild", "root", semplice("b", "3000", "B"));
        exec("addChild", "root", semplice("c", "4000", "C"));

        // Scollega quello centrale (indice 1)
        exec("unlinkNode", "b");
        assertFalse(hasNode("b"));
        assertEquals(2, childCount("root"));

        // Undo: "b" torna nella posizione centrale, non in coda
        undo();
        assertEquals(3, childCount("root"));
        assertEquals(1, childIndexOf("root", "b"), "il figlio scollegato torna nella sua posizione originale");
    }

    @Test
    @DisplayName("Undo dello scollegamento di una SKU la ri-associa (E2)")
    void unlinkSkuRipristina() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("addChild", "root", semplice("cpu", "2000", "CPU"));
        exec("addSku", "cpu", sku("s1", "0001", "Intel", "300"));
        exec("addSku", "cpu", sku("s2", "0002", "AMD", "250"));
        assertEquals(2, skuCount("cpu"));
        assertEquals(2, skuAssociateCount());

        exec("unlinkSku", "cpu", "s1");
        assertFalse(hasSku("cpu", "s1"));
        assertEquals(1, skuCount("cpu"));
        assertEquals(1, skuAssociateCount(), "la lista SKU associate è aggiornata");

        undo();
        assertTrue(hasSku("cpu", "s1"), "la SKU scollegata viene ri-associata");
        assertEquals(2, skuCount("cpu"));
        assertEquals(2, skuAssociateCount(), "anche la lista SKU associate è ripristinata");
    }

    // ========================================================================
    // 7. Edge case: id temporanei preservati negli snapshot (EC6)
    // ========================================================================

    @Test
    @DisplayName("Gli id temporanei sono preservati attraverso undo/redo (EC6)")
    void idTemporaneiPreservati() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("addChild", "root", semplice("temp_169", "2000", "CPU"));
        exec("addSku", "temp_169", sku("temp_sku_42", "0001", "Intel", "300"));

        undo();
        undo();
        assertEquals(0, childCount("root"));

        redo();
        assertTrue(hasNode("temp_169"), "l'id temporaneo del nodo è preservato");
        redo();
        assertTrue(hasSku("temp_169", "temp_sku_42"), "l'id temporaneo della SKU è preservato");
    }

    // ========================================================================
    // 8. Coerenza della coda pendingActions con lo stato (R12)
    // ========================================================================

    @Test
    @DisplayName("La coda pendingActions resta coerente con lo stato dopo undo/redo (R12)")
    void pendingActionsCoerenti() {
        sim.invokeMember("start", composto("root", "1000", "PC"));
        exec("addChild", "root", semplice("a", "2000", "A"));
        exec("addChild", "root", semplice("b", "3000", "B"));
        assertEquals(2, pendingCount());

        undo();
        assertEquals(1, pendingCount(), "l'azione annullata non resta in coda");

        undo();
        assertEquals(0, pendingCount(), "nessuna azione pendente dopo aver annullato tutto");

        redo();
        assertEquals(1, pendingCount(), "il redo ripristina anche la coda");
    }

    // ========================================================================
    // 9. Creazione del prodotto composto radice (azione A) annullabile
    // ========================================================================

    @Test
    @DisplayName("La creazione del prodotto composto radice è annullabile (azione A)")
    void creazioneRadiceUndoable() {
        sim.invokeMember("start", new Object[]{ null }); // sessione senza albero

        exec("createRoot", composto("root", "1000", "PC"));
        assertTrue(hasNode("root"));

        undo();
        assertEquals("null", treeJSON(), "undo riporta all'assenza di albero");
        assertFalse(hasNode("root"));

        redo();
        assertTrue(hasNode("root"));
    }

    // ========================================================================
    // 9b. Edge case: editing con valore vuoto rifiutato, nessun checkpoint (EC2)
    // ========================================================================

    @Test
    @DisplayName("Editing inline con valore vuoto è rifiutato e non genera checkpoint (EC2)")
    void editValoreVuotoRifiutato() {
        sim.invokeMember("start", composto("root", "1000", "PC"));

        boolean committed = exec("editNodeValidato", "root", "nome", "");
        assertFalse(committed, "un valore vuoto non deve generare un checkpoint");
        assertFalse(canUndo());
        assertEquals("PC", nodeField("root", "nome"), "il valore precedente resta invariato");

        // Una modifica valida invece viene registrata
        assertTrue(exec("editNodeValidato", "root", "nome", "Server"));
        assertTrue(canUndo());
    }

    // ========================================================================
    // 10. Scenario combinato lungo con undo/redo intervallati
    // ========================================================================

    @Test
    @DisplayName("Scenario lungo: operazioni, undo intervallati, nuove operazioni e redo")
    void scenarioCombinatoLungo() {
        sim.invokeMember("start", composto("root", "1000", "PC"));

        exec("addChild", "root", composto("g1", "2000", "Gruppo1"));
        exec("addChild", "g1", semplice("cpu", "3000", "CPU"));
        exec("addSku", "cpu", sku("s1", "0001", "Intel", "300"));
        exec("editNode", "cpu", "nome", "Processore");

        // L'utente ci ripensa: annulla SKU e modifica nome
        undo(); // annulla edit nome
        undo(); // annulla SKU
        assertEquals(0, skuCount("cpu"));
        assertEquals("CPU", nodeField("cpu", "nome"));

        // Cambia strada: aggiunge una SKU diversa (svuota il redo)
        exec("addSku", "cpu", sku("s2", "0002", "AMD", "250"));
        assertFalse(canRedo());
        assertTrue(hasSku("cpu", "s2"));
        assertFalse(hasSku("cpu", "s1"));

        // Scollega il gruppo intero e poi annulla
        exec("unlinkNode", "g1");
        assertEquals(0, childCount("root"));
        undo();
        assertEquals(1, childCount("root"));
        assertTrue(hasNode("cpu"));
        assertTrue(hasSku("cpu", "s2"), "l'intero sottoalbero (con la SKU) è ripristinato");
    }
}
