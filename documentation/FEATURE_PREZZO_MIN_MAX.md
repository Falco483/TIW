# Specifiche di Alto Livello — Calcolo Prezzo Min/Max Prodotti

---

## 1. Contesto e Obiettivo

Aggiungere al sistema la capacità di calcolare e memorizzare un **prezzo minimo** e un **prezzo massimo** per ogni prodotto (semplice e composto), con aggiornamento dinamico nel form di creazione prodotto composto in `fornitore/home`.

---

## 2. Modello Dati

### 2.1 Modifiche allo schema DB

Aggiungere due colonne alla tabella `prodotto`:

```sql
ALTER TABLE prodotto
  ADD COLUMN prezzo_min DECIMAL(10,2) NOT NULL DEFAULT 0,
  ADD COLUMN prezzo_max DECIMAL(10,2) NOT NULL DEFAULT 0;
```

### 2.2 Semantica dei valori

| Tipo prodotto | `prezzo_min` | `prezzo_max` |
|---|---|---|
| **Semplice** | Prezzo minimo tra tutti gli SKU associati | Prezzo massimo tra tutti gli SKU associati |
| **Composto** | Valore inserito dal fornitore (≥ somma dei `prezzo_min` dei figli) | Valore inserito dal fornitore (> `prezzo_min` scelto dal fornitore) |

> **Invariante**: per un prodotto composto, il prezzo minimo e massimo sono scelti dal fornitore, ma vincolati dalle regole di validazione (§4).

---

## 3. Calcolo del Prezzo per Prodotti Semplici

Il calcolo avviene **al momento dell'associazione di uno SKU** a un prodotto semplice (e al momento della rimozione, se prevista).

### Regola di calcolo

```
prezzo_min(prodotto_semplice) = MIN(prezzo) su tutti gli SKU associati
prezzo_max(prodotto_semplice) = MAX(prezzo) su tutti gli SKU associati
```

### Dove viene eseguito

- Nel DAO (`ProdottoDAO` o `SKUDAO`), dopo ogni operazione di INSERT/DELETE su `prodotto_sku`.
- Eseguire un `UPDATE prodotto SET prezzo_min=..., prezzo_max=... WHERE codice=?` immediatamente dopo.

---

## 4. Regole di Validazione per Prodotti Composti

Al momento del salvataggio del prodotto composto, il server valida:

| Vincolo | Condizione |
|---|---|
| **V1** | `prezzo_min_utente >= SUM(prezzo_min)` di tutti i sotto-prodotti selezionati |
| **V2** | `prezzo_max_utente > prezzo_min_utente` |
| **V3** | Nessun vincolo superiore su `prezzo_max_utente` (può essere < o > della somma dei massimi) |

Qualunque violazione restituisce un errore con messaggio esplicativo (HTML: forward con `errori`; JS: `400 + JSON`).

---

## 5. Comportamento Frontend — `fornitore/home`

> **Nota versioni**: la versione JS SPA può aggiornare le label in tempo reale tramite listener sulle checkbox. La versione HTML pura non può usare JavaScript: il ricalcolo avviene tramite un submit esplicito del form (pattern "doppio pulsante", descritto di seguito).

### 5.1 Elementi UI da aggiungere al form "Crea Prodotto Composto"

- **Label informativa** "Prezzo min calcolato: `X.XX €`" — aggiornata dopo il ricalcolo
- **Label informativa** "Prezzo max calcolato: `Y.YY €`" — aggiornata dopo il ricalcolo
- **Campo input** `prezzo_min` (numerico, editabile dal fornitore)
- **Campo input** `prezzo_max` (numerico, editabile dal fornitore)
- **Pulsante "Ricalcola prezzi"** — submit intermedio, non salva
- **Pulsante "Salva prodotto"** — submit finale, persiste il prodotto

### 5.2 Pattern "Doppio Pulsante" (versione HTML pura)

Il form usa `method="POST"` con due pulsanti che inviano un parametro `azione` diverso:

```html
<form method="POST" action="/fornitore/home">
  <!-- checkboxes dei sotto-prodotti -->
  ...
  <input type="number" name="prezzo_min" ...>
  <input type="number" name="prezzo_max" ...>

  <button type="submit" name="azione" value="ricalcola">Ricalcola prezzi</button>
  <button type="submit" name="azione" value="salva">Salva prodotto</button>
</form>
```

Il servlet legge `request.getParameter("azione")` e si comporta diversamente:

| Valore `azione` | Comportamento servlet |
|---|---|
| `"ricalcola"` | Legge i `codice` delle checkbox selezionate → carica i prezzi dal DB → somma `prezzo_min` e `prezzo_max` → **forward** alla stessa vista con `prezzoMinCalcolato`, `prezzoMaxCalcolato` e `codiciSelezionati` per ripopolare il form |
| `"salva"` | Valida tutti i campi (§4) → persiste il prodotto → redirect a `fornitore/home` |

La vista Thymeleaf deve:
- Ripopolare le checkbox spuntate usando `codiciSelezionati`
- Mostrare le label calcolate se presenti negli attributi di request
- Impostare l'attributo `min` sull'input `prezzo_min` con il valore di `prezzoMinCalcolato`

### 5.3 Logica dinamica (versione JS SPA)

Ogni volta che una checkbox di sotto-prodotto viene selezionata/deselezionata:

```
prezzo_min_calcolato = Σ prezzo_min di ogni sotto-prodotto selezionato
prezzo_max_calcolato = Σ prezzo_max di ogni sotto-prodotto selezionato
```

Aggiornare le label informative in tempo reale. I valori `prezzo_min` e `prezzo_max` dei sotto-prodotti devono essere inclusi nella risposta JSON dell'API `/api/prodotti`.

### 5.4 Validazione client-side (versione JS SPA)

- `prezzo_min_utente` deve essere `≥ prezzo_min_calcolato` (check JS on-submit)
- `prezzo_max_utente` deve essere `> prezzo_min_utente` (check JS on-submit)
- Mostrare messaggi inline in caso di violazione, prima di inviare la richiesta

---

## 6. Impatto sulle Componenti Esistenti

| Componente | Modifica |
|---|---|
| `Prodotto.java` (model) | Aggiungere campi `prezzoMin`, `prezzoMax` con getter/setter |
| `ProdottoDAO.java` | `calcolaPrezzoSemplice()` (SELECT MIN/MAX da SKU); aggiornamento in `aggiungiSKU()` |
| Servlet HTML `fornitore/home` | Passare `prezzoMin` e `prezzoMax` agli oggetti prodotto nel model della vista |
| Servlet JS `/api/prodotti` | Includere `prezzoMin` e `prezzoMax` nel JSON di risposta |
| Servlet salvataggio prodotto composto | Leggere e validare `prezzoMin`/`prezzoMax` dal body della richiesta |
| Vista Thymeleaf / template HTML | Mostrare le label calcolate e i due nuovi input |
| Script JS del form | Listener su checkbox → ricalcolo somme → aggiornamento DOM → validazione |

---

## 7. Edge Cases da Gestire

- **Prodotto semplice senza SKU**: `prezzo_min = prezzo_max = 0` (o NULL) — il prodotto non è selezionabile come figlio finché non ha almeno uno SKU (vincolo già esistente).
- **Nessun sotto-prodotto selezionato**: i campi prezzo rimangono editabili ma il calcolato mostra `0.00 €`; il server rifiuta comunque un prodotto composto senza figli.
- **Modifica di uno SKU successiva alla creazione del prodotto composto**: i prezzi del prodotto semplice si aggiornano nel DB, ma i prezzi del composto già creato **non si propagano automaticamente** (fuori scope di questa feature).

---

## 8. Fuori Scope

- Ricalcolo a cascata dei prezzi dei prodotti composti quando cambia un SKU di un figlio.
- Storico prezzi o versioning.
- Validazione del prezzo rispetto a valute o IVA.