# Piano di Implementazione — Calcolo Prezzo Min/Max Prodotti

> Riferimento specifiche: `FEATURE_PREZZO_MIN_MAX.md`

---

## Ordine degli Step

```
Step 1 — Schema DB
Step 2 — Model (Prodotto.java)
Step 3 — DAO (ProdottoDAO + SKUDAO)
Step 4 — Servlet HTML (fornitore/home)
Step 5 — Vista Thymeleaf (fornitore/home)
Step 6 — Servlet JS (/api/prodotti)
Step 7 — Frontend JS SPA
Step 8 — Validazione end-to-end e test manuali
```

---

## Step 1 — Aggiornamento Schema DB e Dati di Test

> Il DB viene ricreato da zero: non serve una migrazione ALTER TABLE. Le colonne `prezzo_min` e `prezzo_max` esistono già in `schema.sql` ma erano semanticamente riservate ai prodotti COMPOSTO. Questo step aggiorna commenti e dati di test per riflettere la nuova semantica.

**File coinvolti**: `database/schema.sql`, `database/data_test.sql`, `documentation/CONTRATTO_ARCHITETTURALE.md`

### 1.1 — Aggiornare il commento in `schema.sql` ✅

Il commento della tabella `prodotto` ora documenta la nuova semantica delle colonne:
- `prezzo_min`/`prezzo_max` per **SEMPLICE** → calcolati automaticamente come MIN/MAX dei prezzi degli SKU associati
- `prezzo_min`/`prezzo_max` per **COMPOSTO** → inseriti dal fornitore, con vincolo `prezzo_min >= SUM(prezzo_min dei figli)`

### 1.2 — Popolare `prezzo_min`/`prezzo_max` nei prodotti semplici in `data_test.sql` ✅

I valori inseriti rispecchiano i prezzi degli SKU di test associati:

| Prodotto     | `prezzo_min` | `prezzo_max` | Motivazione                          |
|---|---|---|---|
| CPU          | 150.00       | 250.00       | MIN/MAX tra Ryzen 5 5600, Ryzen 7 5800X, i5-12400 |
| RAM          | 35.00        | 110.00       | MIN/MAX tra 8 GB, 16 GB, 32 GB DDR4  |
| Disco        | 30.00        | 90.00        | MIN/MAX tra SSD 256 GB, 512 GB, 1 TB |
| Scheda Video | 160.00       | 550.00       | MIN/MAX tra GTX 1650, RTX 3060, RTX 4070 |

Il prodotto composto "PC Desktop" mantiene i valori già presenti (prezzo_min=500.00, prezzo_max=3000.00), coerenti con i vincoli: `500 >= 375` (somma minimi figli) ✓ e `3000 > 500` ✓.

### 1.3 — Aggiornare `CONTRATTO_ARCHITETTURALE.md`

Aggiornare la definizione della tabella `prodotto` per documentare la nuova semantica delle colonne `prezzo_min` e `prezzo_max`.

---

## Step 2 — Model: `Prodotto.java` ✅

**File**: `tiw-core/src/main/java/it/polimi/tiw/model/Prodotto.java`

> **Già implementato**: i campi `prezzoMin` e `prezzoMax` erano presenti nella classe prima di questo step, dichiarati come `BigDecimal` (coerente con `DECIMAL(10,2)` nel DB) con getter e setter completi. Nessuna modifica necessaria.

```java
// Già presenti in Prodotto.java:
private BigDecimal prezzoMin;
private BigDecimal prezzoMax;

public BigDecimal getPrezzoMin() { return prezzoMin; }
public void setPrezzoMin(BigDecimal prezzoMin) { this.prezzoMin = prezzoMin; }

public BigDecimal getPrezzoMax() { return prezzoMax; }
public void setPrezzoMax(BigDecimal prezzoMax) { this.prezzoMax = prezzoMax; }
```

> **Nota**: il commento nel campo `descrizione` recitava "NULL per SEMPLICE" riferendosi solo alla descrizione — questo è ancora corretto. I campi `prezzoMin`/`prezzoMax` invece ora hanno valore sia per SEMPLICE che per COMPOSTO (semantica aggiornata in `schema.sql`).

---

## Step 3 — DAO: `ProdottoDAO.java` ✅

> **Nessuna modifica a `SKUDAO.java`**: il calcolo dei prezzi avviene nella servlet, che conosce le SKU selezionate dal form e ne calcola MIN/MAX prima di chiamare il DAO.

### 3.1 — Correggere `mapRow()`: leggere `prezzo_min`/`prezzo_max` anche per SEMPLICE

**File**: `ProdottoDAO.java`

Attualmente `mapRow()` imposta `prezzoMin`/`prezzoMax` **solo per COMPOSTO**. Il branch `else` per SEMPLICE crea un `ProdottoSemplice` senza mai leggere quelle colonne dal `ResultSet`. Correggere:

```java
private Prodotto mapRow(ResultSet rs) throws SQLException {
    Prodotto p;
    if ("COMPOSTO".equals(rs.getString("tipo"))) {
        ProdottoComposto pc = new ProdottoComposto();
        pc.setDescrizione(rs.getString("descrizione"));
        p = pc;
    } else {
        p = new ProdottoSemplice();
    }
    p.setId(rs.getInt("id"));
    p.setCodice(rs.getInt("codice"));
    p.setNome(rs.getString("nome"));
    p.setTipo(rs.getString("tipo"));
    // prezzoMin e prezzoMax ora letti per ENTRAMBI i tipi
    p.setPrezzoMin(rs.getBigDecimal("prezzo_min"));
    p.setPrezzoMax(rs.getBigDecimal("prezzo_max"));
    int idPadre = rs.getInt("id_padre");
    p.setIdPadre(rs.wasNull() ? null : idPadre);
    return p;
}
```

### 3.2 — Aggiornare `insertSemplice()`: accettare `prezzoMin` e `prezzoMax`

Il fornitore seleziona le SKU nel form di creazione del prodotto semplice. La servlet calcolerà `MIN` e `MAX` dei prezzi delle SKU scelte e li passerà al DAO all'atto del salvataggio. La firma del metodo deve quindi accettare i due valori:

```java
public int insertSemplice(String codice, String nome, BigDecimal prezzoMin, BigDecimal prezzoMax)
        throws SQLException {
    String sql = """
        INSERT INTO prodotto (codice, nome, tipo, prezzo_min, prezzo_max)
        VALUES (?, ?, 'SEMPLICE', ?, ?)
        """;
    try (PreparedStatement stmt = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
        stmt.setString(1, codice);
        stmt.setString(2, nome);
        stmt.setBigDecimal(3, prezzoMin);
        stmt.setBigDecimal(4, prezzoMax);
        stmt.executeUpdate();
        try (ResultSet keys = stmt.getGeneratedKeys()) {
            if (keys.next()) return keys.getInt(1);
            throw new SQLException("Insert prodotto semplice non ha restituito un id generato");
        }
    }
}
```

### 3.3 — `insertComposto()`: già corretto ✅

Il metodo accetta già `prezzoMin` e `prezzoMax` come `BigDecimal`. Nessuna modifica necessaria.

---

## Step 4 — Servlet HTML ✅

Questo step riguarda **due servlet distinte**: quella per la creazione del prodotto semplice e quella per la creazione del prodotto composto (`fornitore/home`).

---

### 4A — Servlet creazione prodotto SEMPLICE

**Responsabilità**: leggere le SKU selezionate, calcolarne MIN e MAX, passarli a `insertSemplice()`.

#### 4A.1 — Calcolo prezzoMin/prezzoMax nel `doPost`

Dopo aver validato i parametri base (codice, nome, lista SKU non vuota), aggiungere:

```java
String[] idSkuSelezionate = request.getParameterValues("skuIds"); // ID delle SKU scelte

BigDecimal prezzoMin = null;
BigDecimal prezzoMax = null;

for (String idStr : idSkuSelezionate) {
    SKU sku = skuDAO.findById(Integer.parseInt(idStr));
    if (sku != null) {
        if (prezzoMin == null || sku.getPrezzo().compareTo(prezzoMin) < 0)
            prezzoMin = sku.getPrezzo();
        if (prezzoMax == null || sku.getPrezzo().compareTo(prezzoMax) > 0)
            prezzoMax = sku.getPrezzo();
    }
}
```

#### 4A.2 — Passare i prezzi a `insertSemplice()`

```java
int idNuovoProdotto = prodottoDAO.insertSemplice(codice, nome, prezzoMin, prezzoMax);
// Poi associare le SKU con addSku(idNuovoProdotto, idSku) per ognuna
```

---

### 4B — Servlet creazione prodotto COMPOSTO (`fornitore/home`)

**File**: servlet HTML `fornitore/home` (versione `servlet/html/`)

#### 4B.1 — Gestire il parametro `azione` nel `doPost`

Il metodo `doPost` attuale gestisce il salvataggio del prodotto composto. Aggiungere un branch iniziale:

```java
String azione = request.getParameter("azione");
if ("ricalcola".equals(azione)) {
    gestisciRicalcola(request, response);
} else if ("salva".equals(azione)) {
    gestisciSalva(request, response);
} else {
    response.sendError(HttpServletResponse.SC_BAD_REQUEST);
}
```

#### 4B.2 — Implementare `gestisciRicalcola()`

Calcola la somma dei `prezzoMin`/`prezzoMax` dei sotto-prodotti selezionati e fa forward alla stessa vista per aggiornare le label:

```java
private void gestisciRicalcola(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {

    String[] idSelezionati = request.getParameterValues("sottoprodotti");
    BigDecimal prezzoMinCalcolato = BigDecimal.ZERO;
    BigDecimal prezzoMaxCalcolato = BigDecimal.ZERO;

    if (idSelezionati != null) {
        for (String idStr : idSelezionati) {
            Prodotto p = prodottoDAO.findById(Integer.parseInt(idStr));
            if (p != null && p.getPrezzoMin() != null) {
                prezzoMinCalcolato = prezzoMinCalcolato.add(p.getPrezzoMin());
                prezzoMaxCalcolato = prezzoMaxCalcolato.add(p.getPrezzoMax());
            }
        }
    }

    request.setAttribute("prezzoMinCalcolato", prezzoMinCalcolato);
    request.setAttribute("prezzoMaxCalcolato", prezzoMaxCalcolato);
    request.setAttribute("idSelezionati",
        idSelezionati != null ? Arrays.asList(idSelezionati) : Collections.emptyList());

    // doGet ricarica la lista prodotti e fa il forward alla vista
    doGet(request, response);
}
```

#### 4B.3 — Aggiornare `gestisciSalva()`

Leggere i due nuovi parametri dal form e validarli server-side:

```java
BigDecimal prezzoMin;
BigDecimal prezzoMax;
try {
    prezzoMin = new BigDecimal(request.getParameter("prezzo_min"));
    prezzoMax = new BigDecimal(request.getParameter("prezzo_max"));
} catch (NumberFormatException e) {
    errori.add("Prezzo non valido");
}

// Ricalcolare la somma dei min dei figli selezionati (stessa logica di gestisciRicalcola)
BigDecimal sommaMin = ...; 

if (prezzoMin.compareTo(sommaMin) < 0) {
    errori.add("Il prezzo minimo deve essere almeno " + sommaMin + " €");
}
if (prezzoMax.compareTo(prezzoMin) <= 0) {
    errori.add("Il prezzo massimo deve essere maggiore del prezzo minimo");
}
```

In caso di errori, forward con `request.setAttribute("errori", errori)` e ripopolare il form.

Passare `prezzoMin` e `prezzoMax` a `prodottoDAO.insertComposto()`.

---

## Step 5 — Vista Thymeleaf: `fornitore/home.html`

**File**: template HTML della pagina `fornitore/home` (versione HTML pura)

### 5.1 — Aggiungere le label dei prezzi calcolati

Sopra i campi di input, mostrare i valori calcolati se presenti:

```html
<div th:if="${prezzoMinCalcolato != null}">
  <p>Prezzo min calcolato: <strong th:text="${#numbers.formatDecimal(prezzoMinCalcolato, 1, 2)} + ' €'"></strong></p>
  <p>Prezzo max calcolato: <strong th:text="${#numbers.formatDecimal(prezzoMaxCalcolato, 1, 2)} + ' €'"></strong></p>
</div>
```

### 5.2 — Aggiungere i campi input per i prezzi

```html
<label>Prezzo minimo scelto (€):
  <input type="number" name="prezzo_min" step="0.01"
         th:min="${prezzoMinCalcolato != null ? prezzoMinCalcolato : 0}"
         th:value="${valoriForm != null ? valoriForm['prezzo_min'] : ''}"
         required>
</label>

<label>Prezzo massimo scelto (€):
  <input type="number" name="prezzo_max" step="0.01" min="0"
         th:value="${valoriForm != null ? valoriForm['prezzo_max'] : ''}"
         required>
</label>
```

### 5.3 — Aggiungere i due pulsanti di submit

```html
<button type="submit" name="azione" value="ricalcola">🔄 Ricalcola prezzi</button>
<button type="submit" name="azione" value="salva">💾 Salva prodotto</button>
```

### 5.4 — Ripopolare le checkbox selezionate

Sulla checkbox di ogni sotto-prodotto, aggiungere l'attributo `checked` condizionale:

```html
<input type="checkbox" name="sottoprodotti" th:value="${p.codice}"
       th:checked="${codiciSelezionati != null and codiciSelezionati.contains(p.codice)}">
```

### 5.5 — Mostrare i messaggi di errore

Se già presente una sezione errori nella vista, verificare che includa anche i nuovi errori di validazione prezzo. In caso contrario, aggiungere:

```html
<ul th:if="${errori != null}">
  <li th:each="e : ${errori}" th:text="${e}"></li>
</ul>
```

---

## Step 6 — Servlet JS: `/api/prodotti`

**File**: servlet JS `servlet/js/` che gestisce `GET /api/prodotti` (o `GET /api/prodotti/{codice}/figli`)

### 6.1 — Includere `prezzoMin` e `prezzoMax` nel JSON

Nella serializzazione JSON dell'oggetto `Prodotto`, aggiungere i due campi:

```json
{
  "codice": "...",
  "nome": "...",
  "tipo": "...",
  "prezzoMin": 12.50,
  "prezzoMax": 35.00
}
```

Se si usa una serializzazione manuale con `JSONObject` o simili:

```java
obj.put("prezzoMin", prodotto.getPrezzoMin());
obj.put("prezzoMax", prodotto.getPrezzoMax());
```

Se si usa una libreria come Gson o Jackson, i nuovi campi vengono serializzati automaticamente grazie ai getter aggiunti nel model (Step 2).

---

## Step 7 — Frontend JS SPA

**File**: script JS del form di creazione prodotto composto

### 7.1 — Aggiungere le label nel template HTML della SPA

```html
<p>Prezzo min calcolato: <span id="prezzo-min-calcolato">0.00</span> €</p>
<p>Prezzo max calcolato: <span id="prezzo-max-calcolato">0.00</span> €</p>
<input type="number" id="prezzo-min" name="prezzo_min" step="0.01" min="0">
<input type="number" id="prezzo-max" name="prezzo_max" step="0.01" min="0">
```

### 7.2 — Aggiungere la funzione di ricalcolo

```js
function ricalcolaPrezzi() {
    const checkboxes = document.querySelectorAll('input[name="sottoprodotti"]:checked');
    let minCalcolato = 0;
    let maxCalcolato = 0;

    checkboxes.forEach(cb => {
        minCalcolato += parseFloat(cb.dataset.prezzoMin || 0);
        maxCalcolato += parseFloat(cb.dataset.prezzoMax || 0);
    });

    document.getElementById('prezzo-min-calcolato').textContent = minCalcolato.toFixed(2);
    document.getElementById('prezzo-max-calcolato').textContent = maxCalcolato.toFixed(2);
    document.getElementById('prezzo-min').min = minCalcolato.toFixed(2);
}
```

I valori `prezzo_min` e `prezzo_max` devono essere inseriti come `data-` attribute sulle checkbox quando vengono renderizzate (popolati dalla risposta API di Step 6):

```js
checkbox.dataset.prezzoMin = prodotto.prezzoMin;
checkbox.dataset.prezzoMax = prodotto.prezzoMax;
```

### 7.3 — Agganciare il listener sulle checkbox

```js
// Da aggiungere dopo il rendering dinamico delle checkbox
document.querySelectorAll('input[name="sottoprodotti"]').forEach(cb => {
    cb.addEventListener('change', ricalcolaPrezzi);
});
```

### 7.4 — Validazione client-side on-submit

```js
formElement.addEventListener('submit', (e) => {
    const minCalcolato = parseFloat(document.getElementById('prezzo-min-calcolato').textContent);
    const prezzoMin = parseFloat(document.getElementById('prezzo-min').value);
    const prezzoMax = parseFloat(document.getElementById('prezzo-max').value);

    if (prezzoMin < minCalcolato) {
        e.preventDefault();
        mostraErrore(`Il prezzo minimo deve essere almeno ${minCalcolato.toFixed(2)} €`);
        return;
    }
    if (prezzoMax <= prezzoMin) {
        e.preventDefault();
        mostraErrore('Il prezzo massimo deve essere maggiore del prezzo minimo');
    }
});
```

---

## Step 8 — Validazione End-to-End e Test Manuali

### 8.1 — Checklist DB

- [ ] Le colonne `prezzo_min` e `prezzo_max` esistono nella tabella `prodotto`
- [ ] I prodotti semplici nel `data_test.sql` hanno `prezzo_min`/`prezzo_max` coerenti con i loro SKU

### 8.2 — Checklist versione HTML

- [ ] Selezionando checkbox e cliccando "Ricalcola prezzi" le label si aggiornano
- [ ] Le checkbox rimangono spuntate dopo il ricalcolo
- [ ] L'input `prezzo_min` ha l'attributo `min` impostato al valore calcolato
- [ ] Inserendo `prezzo_min` inferiore alla somma dei minimi → errore server-side
- [ ] Inserendo `prezzo_max` ≤ `prezzo_min` → errore server-side
- [ ] Salvataggio con dati validi → redirect corretto, prodotto presente in DB con prezzi corretti
- [ ] Il form ripopola i valori in caso di errore (campi prezzo e checkbox)

### 8.3 — Checklist versione JS SPA

- [ ] Selezionando/deselezionando una checkbox le label si aggiornano in tempo reale
- [ ] L'API `/api/prodotti` restituisce `prezzoMin` e `prezzoMax` per ogni prodotto
- [ ] La validazione client-side blocca il submit con messaggio inline
- [ ] La validazione server-side risponde con `400` e JSON `{"errore": "..."}` in caso di violazione
- [ ] Salvataggio con dati validi → risposta `200/201`, prodotto salvato con prezzi corretti

### 8.4 — Edge cases da testare

- [ ] Prodotto semplice con una sola SKU: `prezzo_min == prezzo_max` — corretto
- [ ] Prodotto semplice con tutte SKU allo stesso prezzo: `prezzo_min == prezzo_max` — corretto
- [ ] Prodotto composto con un solo figlio: i calcoli funzionano come con N figli
- [ ] Nessun sotto-prodotto selezionato nel form composto: label mostrano `0.00 €`, il server rifiuta il salvataggio
