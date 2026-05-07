# API Contract — TIW Configuratore di Prodotto (Versione SPA)

> **Versione**: 2.0 (post-rollback architetturale)  
> **Data**: 2026-05-03  
> **Content-Type**: `application/json; charset=UTF-8`  
> **Autenticazione**: HttpSession (cookie `JSESSIONID`, gestito da Tomcat)  
> **Protezione CSRF**: Header `X-CSRF-Token` obbligatorio su POST/PUT/DELETE

---

## Convenzioni Generali

### Autenticazione — HttpSession

L'autenticazione è **stateful**: al login il server crea una `HttpSession` e Tomcat
invia un cookie `JSESSIONID` al browser. Da quel momento, il browser lo include
automaticamente in ogni richiesta verso lo stesso dominio.

**Non serve nessun header `Authorization`.** Il cookie basta.

Se la sessione è scaduta o l'utente non è loggato, il server risponde `401`.

### Protezione CSRF — Synchronizer Token

Ogni richiesta **mutante** (POST, PUT, DELETE) deve includere il token CSRF
nell'header HTTP custom:

```
X-CSRF-Token: <token>
```

**Come ottenere il token nella SPA:**  
Al primo caricamento della pagina, il server inietta il token nel DOM (attributo
`data-csrf` o meta tag). Il JavaScript lo legge e lo include in ogni `fetch()`:

```javascript
const csrfToken = document.querySelector('meta[name="csrf-token"]').content;

fetch('/api/prodotti', {
    method: 'POST',
    headers: {
        'Content-Type': 'application/json',
        'X-CSRF-Token': csrfToken
    },
    body: JSON.stringify(payload)
});
```

Senza il token, o con un token errato → `403 Forbidden`.

### Formato Risposta Standard

**Successo:**
```json
{ "data": { ... } }
```
oppure
```json
{ "data": [ ... ] }
```

**Errore:**
```json
{
  "errore": "Messaggio leggibile dall'utente",
  "codice": "CODICE_ERRORE"
}
```

### Codici HTTP

| Codice | Significato | Quando |
|--------|-------------|--------|
| `200`  | OK | Operazione riuscita (GET, PUT, DELETE) |
| `201`  | Created | Risorsa creata (POST) |
| `400`  | Bad Request | Validazione fallita, input malformato |
| `401`  | Unauthorized | Sessione assente o scaduta |
| `403`  | Forbidden | Ruolo sbagliato, o token CSRF mancante/invalido |
| `404`  | Not Found | Risorsa inesistente |
| `409`  | Conflict | Codice duplicato, vincolo violato |
| `500`  | Internal Server Error | Errore server imprevisto |

### Ruoli

| Ruolo | Descrizione |
|-------|-------------|
| `FORNITORE` | Gestisce catalogo (CRUD prodotti e SKU) |
| `CLIENTE` | Naviga catalogo, crea/gestisce configurazioni |
| `PUBBLICO` | Solo login, nessuna sessione richiesta |

---

## 1. Autenticazione

### `POST /api/login`

> **Ruolo**: PUBBLICO  
> **CSRF**: Non richiesto (l'utente non ha ancora una sessione)

Verifica le credenziali, crea la `HttpSession`, salva l'oggetto Utente in sessione.
Tomcat invia automaticamente il cookie `JSESSIONID` nella risposta.

**Request Body:**
```json
{
  "username": "mario.rossi",
  "password": "SecureP4ss!"
}
```

**Response `200 OK`:**
```json
{
  "data": {
    "username": "mario.rossi",
    "nome": "Mario",
    "cognome": "Rossi",
    "ruolo": "CLIENTE",
    "csrfToken": "a1B2c3D4e5F6..."
  }
}
```

> Il `csrfToken` viene restituito nella risposta di login affinché la SPA
> possa salvarlo in memoria e includerlo nelle richieste successive.

**Response `401 Unauthorized`:**
```json
{
  "errore": "Credenziali non valide",
  "codice": "AUTH_INVALID_CREDENTIALS"
}
```

---

### `POST /api/logout`

> **Ruolo**: FORNITORE, CLIENTE  
> **CSRF**: Richiesto

Invalida la sessione corrente (`session.invalidate()`).

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "Logout effettuato" }
}
```

---

## 2. Prodotti

### `GET /api/prodotti`

> **Ruolo**: FORNITORE, CLIENTE

Lista prodotti radice (`id_padre IS NULL`).
Per il CLIENTE: solo COMPOSTI, paginati, ordinati per nome DESC.

**Query Parameters (CLIENTE):**

| Parametro | Tipo | Default | Descrizione |
|-----------|------|---------|-------------|
| `pagina`  | int  | 1       | Numero pagina (1-indexed) |
| `perPagina` | int | 10    | Elementi per pagina (max 10 da specifica) |

**Response `200 OK` (CLIENTE — paginata):**
```json
{
  "data": {
    "prodotti": [
      {
        "id": 1,
        "codice": "PC001",
        "nome": "PC Desktop Gaming",
        "tipo": "COMPOSTO",
        "descrizione": "PC configurabile ad alte prestazioni",
        "prezzoMin": 500.00,
        "prezzoMax": 3000.00
      }
    ],
    "paginazione": {
      "paginaCorrente": 1,
      "totalePagine": 3,
      "totaleElementi": 25,
      "hasPrecedente": false,
      "hasSuccessiva": true
    }
  }
}
```

**Response `200 OK` (FORNITORE — lista completa):**
```json
{
  "data": [
    {
      "id": 1,
      "codice": "PC001",
      "nome": "PC Desktop Gaming",
      "tipo": "COMPOSTO",
      "descrizione": "...",
      "prezzoMin": 500.00,
      "prezzoMax": 3000.00
    },
    {
      "id": 5,
      "codice": "CPU01",
      "nome": "CPU",
      "tipo": "SEMPLICE"
    }
  ]
}
```

---

### `GET /api/prodotti/{codice}`

> **Ruolo**: FORNITORE, CLIENTE

Dettaglio di un prodotto con albero gerarchico completo (figli ricorsivi + SKU per i semplici).

**Response `200 OK`:**
```json
{
  "data": {
    "id": 1,
    "codice": "PC001",
    "nome": "PC Desktop Gaming",
    "tipo": "COMPOSTO",
    "descrizione": "PC configurabile",
    "prezzoMin": 500.00,
    "prezzoMax": 3000.00,
    "figli": [
      {
        "id": 2,
        "codice": "CASE01",
        "nome": "Case",
        "tipo": "SEMPLICE",
        "skus": [
          {
            "id": 1,
            "codice": 1001,
            "nome": "Fractal Design North",
            "fotografia": "/img/sku/fractal-north.jpg",
            "descrizioneTecnica": "Mid-tower ATX, mesh front",
            "prezzo": 129.90
          }
        ]
      },
      {
        "id": 3,
        "codice": "ELAB01",
        "nome": "Sistema di Elaborazione",
        "tipo": "COMPOSTO",
        "figli": [
          {
            "id": 6,
            "codice": "CPU01",
            "nome": "CPU",
            "tipo": "SEMPLICE",
            "skus": [ "..." ]
          }
        ]
      }
    ]
  }
}
```

**Response `404 Not Found`:**
```json
{
  "errore": "Prodotto con codice 'XYZ' non trovato",
  "codice": "PRODOTTO_NOT_FOUND"
}
```

---

### `POST /api/prodotti`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Crea un nuovo prodotto (semplice o composto).

**Request Body (SEMPLICE):**
```json
{
  "codice": "CPU01",
  "nome": "CPU",
  "tipo": "SEMPLICE",
  "skuIds": [1, 3, 7]
}
```

**Request Body (COMPOSTO):**
```json
{
  "codice": "PC001",
  "nome": "PC Desktop Gaming",
  "tipo": "COMPOSTO",
  "descrizione": "PC configurabile ad alte prestazioni",
  "prezzoMin": 500.00,
  "prezzoMax": 3000.00,
  "figliIds": [2, 3, 4]
}
```

**Response `201 Created`:**
```json
{
  "data": {
    "id": 1,
    "codice": "PC001",
    "nome": "PC Desktop Gaming",
    "tipo": "COMPOSTO"
  }
}
```

**Response `400 Bad Request`:**
```json
{
  "errore": "Il campo 'nome' è obbligatorio",
  "codice": "VALIDATION_ERROR"
}
```

**Response `409 Conflict`:**
```json
{
  "errore": "Esiste già un prodotto con codice 'PC001'",
  "codice": "CODICE_DUPLICATO"
}
```

---

### `PATCH /api/prodotti/{codice}`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Modifica inline uno o più attributi di un prodotto (click su attributo → campo editabile → salva su blur).
Solo i campi inviati nel body vengono aggiornati (partial update).

**Request Body (parziale — solo i campi da modificare):**
```json
{
  "nome": "PC Desktop Gaming Pro",
  "descrizione": "PC configurabile ad altissime prestazioni",
  "prezzoMin": 600.00,
  "prezzoMax": 3500.00
}
```

**Response `200 OK`:**
```json
{
  "data": {
    "id": 1,
    "codice": "PC001",
    "nome": "PC Desktop Gaming Pro",
    "tipo": "COMPOSTO",
    "descrizione": "PC configurabile ad altissime prestazioni",
    "prezzoMin": 600.00,
    "prezzoMax": 3500.00,
    "messaggio": "Prodotto aggiornato"
  }
}
```

**Response `400 Bad Request`:**
```json
{
  "errore": "prezzoMax deve essere >= prezzoMin",
  "codice": "VALIDATION_ERROR"
}
```

**Response `404 Not Found`:**
```json
{
  "errore": "Prodotto con codice 'XYZ' non trovato",
  "codice": "PRODOTTO_NOT_FOUND"
}
```

---

### `POST /api/prodotti/{codice}/figli`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Aggiunge un sottoprodotto alla gerarchia.

**Request Body:**
```json
{
  "figlioCodice": "CPU01"
}
```

**Response `201 Created`:**
```json
{
  "data": { "messaggio": "Sottoprodotto aggiunto" }
}
```

**Response `400 Bad Request`:**
```json
{
  "errore": "Profondità massima (4 livelli) superata",
  "codice": "MAX_DEPTH_EXCEEDED"
}
```

**Response `409 Conflict`:**
```json
{
  "errore": "Aggiunta bloccata: creerebbe un ciclo nella gerarchia",
  "codice": "CYCLE_DETECTED"
}
```

---

### `DELETE /api/prodotti/{codice}/figli/{figlioCodice}`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Rimuove la relazione padre-figlio. Il figlio resta nel DB come prodotto radice.

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "Relazione rimossa" }
}
```

---

### `DELETE /api/prodotti/{codice}`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Elimina un prodotto e tutta la sottogerarchia (CASCADE nel DB).

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "Prodotto e sotto-gerarchia eliminati" }
}
```

**Response `409 Conflict`:**
```json
{
  "errore": "SKU referenziate in configurazioni esistenti",
  "codice": "SKU_IN_USE"
}
```

---

### `POST /api/prodotti/{codice}/sku`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Associa una SKU esistente a un prodotto semplice.

**Request Body:**
```json
{
  "skuId": 5
}
```

**Response `201 Created`:**
```json
{
  "data": { "messaggio": "SKU associata al prodotto" }
}
```

---

### `DELETE /api/prodotti/{codice}/sku/{skuId}`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Rimuove l'associazione SKU-prodotto.

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "Associazione SKU rimossa" }
}
```

---

## 3. SKU

### `POST /api/sku`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Crea una nuova SKU.

**Request Body:**
```json
{
  "codice": 2001,
  "nome": "AMD Ryzen 7 7700X",
  "fotografia": "/img/sku/ryzen7-7700x.jpg",
  "descrizioneTecnica": "8 core, 16 thread, 4.5 GHz base, 5.4 GHz boost, AM5",
  "prezzo": 329.00
}
```

**Response `201 Created`:**
```json
{
  "data": {
    "id": 5,
    "codice": 2001,
    "nome": "AMD Ryzen 7 7700X",
    "prezzo": 329.00
  }
}
```

---

### `GET /api/sku`

> **Ruolo**: FORNITORE

Lista tutte le SKU, ordinamento per codice decrescente.

**Response `200 OK`:**
```json
{
  "data": [
    {
      "id": 5,
      "codice": 2001,
      "nome": "AMD Ryzen 7 7700X",
      "fotografia": "/img/sku/ryzen7-7700x.jpg",
      "descrizioneTecnica": "8 core, 16 thread...",
      "prezzo": 329.00
    }
  ]
}
```

---

### `PATCH /api/sku/{id}`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Aggiorna uno o più attributi di una SKU (edit inline nella SPA, salvataggio su blur).

**Request Body (parziale):**
```json
{
  "nome": "AMD Ryzen 7 7700X (Rev.B)",
  "prezzo": 299.00
}
```

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "SKU aggiornata" }
}
```

---

### `DELETE /api/sku/{id}`

> **Ruolo**: FORNITORE  
> **CSRF**: Richiesto

Elimina una SKU dal database.

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "SKU eliminata" }
}
```

**Response `409 Conflict`:**
```json
{
  "errore": "SKU referenziata in configurazioni esistenti, impossibile eliminare",
  "codice": "SKU_IN_USE"
}
```

---

## 4. Ricerca

### `GET /api/ricerca`

> **Ruolo**: FORNITORE

Ricerca case-insensitive su nome/descrizione di prodotti e nome/descrizione tecnica di SKU.

**Query Parameters:**

| Parametro | Tipo   | Obbligatorio | Descrizione |
|-----------|--------|:------------:|-------------|
| `q`       | string | ✅           | Termine di ricerca (min 1 carattere) |

**Response `200 OK`:**
```json
{
  "data": {
    "prodotti": [
      {
        "id": 1,
        "codice": "PC001",
        "nome": "PC Desktop Gaming",
        "tipo": "COMPOSTO"
      }
    ],
    "skus": [
      {
        "id": 5,
        "codice": 2001,
        "nome": "AMD Ryzen 7 7700X",
        "prezzo": 329.00
      }
    ]
  }
}
```

---

## 5. Configurazioni

### `GET /api/configurazioni`

> **Ruolo**: CLIENTE

Lista tutte le configurazioni del cliente autenticato, ordinate per data decrescente.
Il backend filtra automaticamente per `cliente_username = session.utente.username`.

**Response `200 OK`:**
```json
{
  "data": [
    {
      "id": 1,
      "nome": "Il mio PC Gaming",
      "dataCreazione": "2026-05-02T14:30:00",
      "dataModifica": "2026-05-02T15:00:00",
      "prezzoTotale": 1547.80,
      "prodottoRadice": {
        "codice": "PC001",
        "nome": "PC Desktop Gaming"
      }
    }
  ]
}
```

---

### `POST /api/configurazioni`

> **Ruolo**: CLIENTE  
> **CSRF**: Richiesto

Salva una nuova configurazione. Il backend:
1. Verifica che ogni prodotto semplice nell'albero abbia una SKU selezionata
2. Verifica che ogni SKU selezionata sia effettivamente associata al prodotto
3. Congela i prezzi correnti delle SKU in `configurazione_dettaglio`
4. Calcola e salva il `prezzo_totale`

**Request Body:**
```json
{
  "nome": "Il mio PC Gaming",
  "prodottoRadiceCodice": "PC001",
  "selezioni": [
    { "prodottoCodice": "CASE01", "skuId": 1 },
    { "prodottoCodice": "CPU01",  "skuId": 5 },
    { "prodottoCodice": "GPU01",  "skuId": 9 },
    { "prodottoCodice": "RAM01",  "skuId": 12 },
    { "prodottoCodice": "SSD01",  "skuId": 15 }
  ]
}
```

**Response `201 Created`:**
```json
{
  "data": {
    "id": 4,
    "nome": "Il mio PC Gaming",
    "dataCreazione": "2026-05-02T16:45:00",
    "prezzoTotale": 1547.80,
    "dettagli": [
      {
        "prodottoCodice": "CASE01",
        "prodottoNome": "Case",
        "skuNome": "Fractal Design North",
        "prezzoCongelato": 129.90
      },
      {
        "prodottoCodice": "CPU01",
        "prodottoNome": "CPU",
        "skuNome": "AMD Ryzen 7 7700X",
        "prezzoCongelato": 329.00
      }
    ]
  }
}
```

**Response `400 Bad Request`:**
```json
{
  "errore": "Manca la selezione SKU per il prodotto 'RAM01'",
  "codice": "SELEZIONE_INCOMPLETA"
}
```

**Response `400 Bad Request` (SKU non associata):**
```json
{
  "errore": "La SKU 99 non è associata al prodotto 'CPU01'",
  "codice": "SKU_NON_ASSOCIATA"
}
```

---

### `GET /api/configurazioni/{id}`

> **Ruolo**: CLIENTE

Dettaglio configurazione con SKU selezionate e prezzi congelati.
Il backend verifica che `configurazione.cliente_username == utente in sessione`.

**Response `200 OK`:**
```json
{
  "data": {
    "id": 1,
    "nome": "Il mio PC Gaming",
    "dataCreazione": "2026-05-02T14:30:00",
    "dataModifica": "2026-05-02T15:00:00",
    "prezzoTotale": 1547.80,
    "prodottoRadice": {
      "codice": "PC001",
      "nome": "PC Desktop Gaming"
    },
    "dettagli": [
      {
        "prodottoId": 2,
        "prodottoCodice": "CASE01",
        "prodottoNome": "Case",
        "skuId": 1,
        "skuCodice": 1001,
        "skuNome": "Fractal Design North",
        "skuFotografia": "/img/sku/fractal-north.jpg",
        "prezzoCongelato": 129.90
      }
    ]
  }
}
```

**Response `403 Forbidden`:**
```json
{
  "errore": "Non puoi accedere alle configurazioni di un altro utente",
  "codice": "ACCESS_DENIED"
}
```

---

### `PUT /api/configurazioni/{id}`

> **Ruolo**: CLIENTE  
> **CSRF**: Richiesto

Modifica le selezioni SKU di una configurazione esistente.
Aggiorna `data_modifica` e ricalcola `prezzo_totale` con snapshotting.

**Request Body:**
```json
{
  "selezioni": [
    { "prodottoCodice": "CASE01", "skuId": 2 },
    { "prodottoCodice": "CPU01",  "skuId": 5 },
    { "prodottoCodice": "GPU01",  "skuId": 10 },
    { "prodottoCodice": "RAM01",  "skuId": 12 },
    { "prodottoCodice": "SSD01",  "skuId": 16 }
  ]
}
```

**Response `200 OK`:**
```json
{
  "data": {
    "id": 1,
    "messaggio": "Configurazione aggiornata",
    "prezzoTotale": 1623.50
  }
}
```

---

### `DELETE /api/configurazioni/{id}`

> **Ruolo**: CLIENTE  
> **CSRF**: Richiesto

Cancella una configurazione e tutti i suoi dettagli (CASCADE).

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "Configurazione eliminata" }
}
```

**Response `403 Forbidden`:**
```json
{
  "errore": "Non puoi eliminare configurazioni di un altro utente",
  "codice": "ACCESS_DENIED"
}
```

---

### `POST /api/configurazioni/{id}/clona`

> **Ruolo**: CLIENTE  
> **CSRF**: Richiesto

Clona una configurazione: nuova riga con nome `"Copia di {originale}"`,
data attuale, stessi dettagli con gli stessi prezzi congelati.

**Response `201 Created`:**
```json
{
  "data": {
    "id": 8,
    "nome": "Copia di Il mio PC Gaming",
    "dataCreazione": "2026-05-03T17:00:00",
    "prezzoTotale": 1547.80
  }
}
```

---

## Riepilogo Endpoint

| # | Metodo | URI | Ruolo | CSRF |
|---|--------|-----|-------|:----:|
| 1 | `POST` | `/api/login` | PUBBLICO | No |
| 2 | `POST` | `/api/logout` | TUTTI | Sì |
| 3 | `GET` | `/api/prodotti` | FORNITORE, CLIENTE | — |
| 4 | `GET` | `/api/prodotti/{codice}` | FORNITORE, CLIENTE | — |
| 5 | `POST` | `/api/prodotti` | FORNITORE | Sì |
| 6 | `PATCH` | `/api/prodotti/{codice}` | FORNITORE | Sì |
| 7 | `DELETE` | `/api/prodotti/{codice}` | FORNITORE | Sì |
| 8 | `POST` | `/api/prodotti/{codice}/figli` | FORNITORE | Sì |
| 9 | `DELETE` | `/api/prodotti/{codice}/figli/{figlioCodice}` | FORNITORE | Sì |
| 10 | `POST` | `/api/prodotti/{codice}/sku` | FORNITORE | Sì |
| 11 | `DELETE` | `/api/prodotti/{codice}/sku/{skuId}` | FORNITORE | Sì |
| 12 | `POST` | `/api/sku` | FORNITORE | Sì |
| 13 | `GET` | `/api/sku` | FORNITORE | — |
| 14 | `PATCH` | `/api/sku/{id}` | FORNITORE | Sì |
| 15 | `DELETE` | `/api/sku/{id}` | FORNITORE | Sì |
| 16 | `GET` | `/api/ricerca?q=...` | FORNITORE | — |
| 17 | `GET` | `/api/configurazioni` | CLIENTE | — |
| 18 | `POST` | `/api/configurazioni` | CLIENTE | Sì |
| 19 | `GET` | `/api/configurazioni/{id}` | CLIENTE | — |
| 20 | `PUT` | `/api/configurazioni/{id}` | CLIENTE | Sì |
| 21 | `DELETE` | `/api/configurazioni/{id}` | CLIENTE | Sì |
| 22 | `POST` | `/api/configurazioni/{id}/clona` | CLIENTE | Sì |

---

*Documento aggiornato: 2026-05-03 — v2.1 post-ristrutturazione multi-module.*  
*Auth: HttpSession + JSESSIONID. CSRF: Synchronizer Token via X-CSRF-Token header.*

