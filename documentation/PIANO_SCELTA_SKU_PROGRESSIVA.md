# Piano: Navigazione Progressiva nella Pagina SCELTA SKU

## Contesto

Le specifiche richiedono che nella pagina SCELTA SKU i sottolivelli dell'albero vengano rivelati progressivamente: solo dopo che l'utente clicca su un prodotto composto di livello N compaiono i suoi figli di livello N+1. L'implementazione attuale (`configura.html`) carica e mostra l'intero albero in un'unica risposta Thymeleaf, senza interazione progressiva.

La versione HTML del progetto non può contenere JavaScript. L'approccio adottato è quindi la navigazione progressiva **server-side** tramite form POST e redirect GET (pattern PRG), identico a quello già usato in `HomeFornitoreServlet` per la gestione degli errori con `valoriForm`.

**Obiettivo**: permettere all'utente di espandere un nodo composto con un click, senza perdere le selezioni SKU già effettuate.

---

## File da modificare

| File | Modifica |
|------|----------|
| `tiw-ssr/.../servlet/web/ConfiguraServlet.java` | Aggiungere `doPost()` con branch espandi/salva |
| `tiw-ssr/.../webapp/WEB-INF/templates/configura.html` | Refactoring del form e del fragment ricorsivo |
| `tiw-ssr/.../servlet/web/SalvaConfigurazioneServlet.java` | Il metodo `ritornaAllaFormConErrore` deve ora salvare in sessione + redirect |

---

## Architettura della soluzione

### Principio chiave

Un **unico form** contiene tutti gli elementi interattivi della pagina (SKU select, pulsanti espandi, input nome, pulsante salva). In questo modo, quando l'utente clicca "Espandi", il form invia al server **tutti** i `sku_*` già selezionati insieme all'ID del nodo da espandere.

Il server distingue le due azioni in base alla presenza del parametro `espandi` nel POST:
- Parametro `espandi` presente → branch espansione
- Parametro `espandi` assente → branch salvataggio

### Stato tracciato via URL + sessione

- **`aperto`**: lista degli ID dei prodotti composti già espansi, trasportata come parametri GET ripetuti nell'URL (`?codice=1&aperto=5&aperto=7`). Non contiene la radice (sempre mostrata).
- **`configura.mappaScelte`**: `Map<Integer,Integer>` (prodottoId→skuId) salvata in sessione durante un'espansione, letta e cancellata al successivo GET.
- **`configura.errore`**, **`configura.nomeInserito`**, **`configura.apertoSet`**: salvati in sessione in caso di errore di validazione al salvataggio, per ripresentare la form intatta.

---

## Implementazione dettagliata

### 1. `ConfiguraServlet.java`

**Aggiungere `doPost()`**:

```
doPost(request, response):
  1. Legge codiceRadice (hidden field nel form)
  2. Raccoglie tutte le selezioni SKU correnti:
       Map<Integer,Integer> mappaScelte = {}
       per ogni param che inizia con "sku_":
         mappaScelte.put(idProdotto, idSku)
  3. Raccoglie l'insieme degli ID già aperti:
       Set<Integer> apertoSet = leggi parametri "aperto" dal form (hidden fields)

  4. Se request.getParameter("espandi") != null:
       int espandiId = parseInt(request.getParameter("espandi"))
       apertoSet.add(espandiId)
       session.setAttribute("configura.mappaScelte", mappaScelte)
       buildRedirectUrl(codice, apertoSet) → /cliente/configura?codice=X&aperto=A&aperto=B...
       response.sendRedirect(url)
       return

  5. Altrimenti (azione = salva):
       Delega a handleSalva(request, response, mappaScelte, apertoSet)
```

**`handleSalva` (logica già in `SalvaConfigurazioneServlet`, qui estratta o invocata)**:
- Legge `nomeConfigurazione`, `idModifica`, `codiceRadice`
- Valida e salva nel DB (stessa logica di `SalvaConfigurazioneServlet.doPost`)
- In caso di errore: salva in sessione `configura.mappaScelte`, `configura.errore`, `configura.nomeInserito`, `configura.apertoSet`, poi redirect GET con gli stessi `aperto` params
- In caso di successo: redirect a `/cliente/configurazioni`

**Modificare `doGet()`**:
- Legge `aperto[]` dai parametri GET → `Set<Integer> apertoSet`
- Legge `configura.mappaScelte` dalla sessione (e la rimuove)
- Legge `configura.errore`, `configura.nomeInserito`, `configura.apertoSet` dalla sessione (e li rimuove)
- Passa tutto al template: `radice`, `apertoSet`, `mappaScelte`, `idConfigInModifica`, `nomeConfigurazione`, `errore`, `nomeInserito`

**Costanti di sessione** da aggiungere in cima alla classe:
```java
private static final String SESSION_MAPPA_SCELTE = "configura.mappaScelte";
private static final String SESSION_ERRORE       = "configura.errore";
private static final String SESSION_NOME_INSERITO = "configura.nomeInserito";
private static final String SESSION_APERTO_SET   = "configura.apertoSet";
```

**Nota sulla duplicazione con `SalvaConfigurazioneServlet`**: la logica di salvataggio può essere estratta in un metodo privato statico o in una classe utility `ConfigurazioneSaver`. In alternativa, `SalvaConfigurazioneServlet` rimane invariato e il `doPost` di `ConfiguraServlet` (branch salva) vi **delega** internamente tramite `request.getRequestDispatcher("/cliente/salva").forward(request, response)`.

> ⚠️ L'opzione forward funziona perché il forward è server-side e trasferisce il corpo POST integro. Questa è la soluzione più semplice: nessuna duplicazione di codice.

---

### 2. `configura.html` — Refactoring del template

**Struttura del form (unico)**:
```html
<form method="POST" th:action="@{/cliente/configura}">
  <!-- Campi nascosti globali -->
  <input type="hidden" name="_csrf" th:value="${session.csrfToken}" />
  <input type="hidden" name="codiceRadice" th:value="${radice.codice}" />
  <input type="hidden" name="idModifica" th:value="${idConfigInModifica}" />
  <!-- Ripropone gli ID già aperti (letti dal set passato dal servlet) -->
  <input th:each="id : ${apertoSet}" type="hidden" name="aperto" th:value="${id}" />

  <!-- Messaggio di errore (se presente) -->
  <div th:if="${errore != null}" th:text="${errore}" style="color:red;"></div>

  <!-- Albero prodotti (fragment ricorsivo) -->
  <ul>
    <li th:replace="~{::albero(${radice}, true)}"></li>
  </ul>

  <!-- Nome + salva (sempre visibili in fondo) -->
  <label>Nome configurazione:
    <input type="text" name="nomeConfigurazione" required
           th:value="${nomeInserito != null ? nomeInserito : nomeConfigurazione}" />
  </label>
  <button type="submit"
          th:text="${idConfigInModifica != null ? 'Aggiorna' : 'Salva Configurazione'}">
  </button>
</form>
```

**Fragment `albero(nodo, isRadice)`**:
```
albero(nodo, isRadice):
  mostra nome del nodo

  se nodo.tipo == 'SEMPLICE':
    → <select name="sku_${nodo.id}" required>
        <option value="" disabled th:selected="nessuna scelta in mappaScelte">Scegli...</option>
        <option th:each="sku" th:selected="mappaScelte.get(nodo.id) == sku.id">...</option>
      </select>

  se nodo.tipo == 'COMPOSTO':
    se isRadice == true OPPURE nodo.id è in apertoSet:
      → <ul> ricorsione sui figli con isRadice=false </ul>
    altrimenti (nodo composto non ancora espanso):
      → <button type="submit" name="espandi" th:value="${nodo.id}">Espandi ▶</button>
```

La radice è sempre mostrata espansa (si passa `isRadice=true` solo per la prima chiamata). Tutti i nodi compositi a qualunque livello, se non nel `apertoSet`, mostrano il bottone Espandi.

---

### 3. `SalvaConfigurazioneServlet.java` — Adattamento `ritornaAllaFormConErrore`

Il metodo attuale fa un forward diretto al template. Con la nuova architettura (unico entry point su `/cliente/configura`), il comportamento corretto è:

1. Salvare in sessione: `mappaScelte`, `errore`, `nomeInserito`, `apertoSet`
2. Fare redirect GET a `/cliente/configura?codice=X&aperto=A&aperto=B...`

Questo garantisce che l'utente veda la stessa visualizzazione (stessi livelli aperti) con le stesse selezioni SKU pre-popolate.

> Se si sceglie l'opzione forward da `ConfiguraServlet` a `SalvaConfigurazioneServlet`, questo file non va modificato: l'errore viene gestito tornando al chiamante `ConfiguraServlet.handleSalva`.

---

## Flusso utente completo (dopo la modifica)

```
HOME CLIENTE
  → click su prodotto composto di 1° livello
  → GET /cliente/configura?codice=1
     apertoSet = {} (vuoto)
     → mostra radice + figli di 2° livello
       - semplici: dropdown SKU
       - composti: bottone "Espandi"

  → utente seleziona SKU per prodotti semplici visibili
  → utente clicca "Espandi" su prodotto composto di 2° livello (id=5)
  → POST /cliente/configura
     params: codiceRadice=1, aperto=[], espandi=5, sku_3=7, sku_4=9
     → salva mappaScelte={3→7, 4→9} in sessione
     → redirect GET /cliente/configura?codice=1&aperto=5

  → GET /cliente/configura?codice=1&aperto=5
     apertoSet = {5}
     mappaScelte = {3→7, 4→9} (da sessione, pre-selezionate)
     → mostra albero con livello 3 visibile sotto id=5
     → i dropdown per id=3 e id=4 sono già pre-selezionati

  → utente seleziona le rimanenti SKU
  → utente clicca "Salva Configurazione"
  → POST /cliente/configura
     params: codiceRadice=1, aperto=[5], sku_3=7, sku_4=9, sku_8=12, nomeConfigurazione="Config 1"
     → nessun param "espandi" → branch salva
     → forward a /cliente/salva (o logica inline)
     → redirect a /cliente/configurazioni
```

---

## Verifica end-to-end

1. **Test espansione**: accedere a `/cliente/configura?codice=<id_composto>`, verificare che i figli di 2° livello siano visibili e i compositi abbiano il bottone Espandi.
2. **Test preservazione selezioni**: selezionare una SKU, cliccare Espandi, verificare che la SKU selezionata sia ancora pre-selezionata dopo il redirect.
3. **Test salvataggio**: compilare tutti i dropdown e salvare; verificare redirect a `/cliente/configurazioni` e presenza della nuova configurazione.
4. **Test errore nome mancante**: lasciare vuoto il nome e cliccare Salva; verificare che la form si ripresenti con i dropdown pre-selezionati e i livelli aperti invariati.
5. **Test modifica**: da `/cliente/configurazioni` cliccare Modifica; verificare che i dropdown siano pre-popolati con le scelte precedenti e i livelli siano aperti correttamente.
6. **Test CSRF**: verificare che una POST senza token venga rifiutata dal `CsrfFilter`.
