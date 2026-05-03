# API Contract — TIW Configuratore di Prodotto

> **Versione**: 1.0  
> **Data**: 2026-05-02  
> **Protocollo**: HTTP/1.1 over HTTPS  
> **Content-Type**: `application/json; charset=UTF-8`  
> **Autenticazione**: Bearer Token (JWT) nell'header `Authorization`

---

## Convenzioni Generali

### Autenticazione

Tutti gli endpoint (tranne `/api/auth/login`) richiedono il JWT nell'header:

```
Authorization: Bearer <token>
```

### Formato Risposta Standard

**Successo:**
```json
{ "data": { ... } }
```

**Errore:**
```json
{
  "errore": "Messaggio leggibile",
  "codice": "CODICE_ERRORE"
}
```

### Codici di Errore HTTP

| Codice | Significato | Quando |
|--------|-------------|--------|
| `200`  | OK | Operazione riuscita (GET, PUT) |
| `201`  | Created | Risorsa creata (POST) |
| `400`  | Bad Request | Validazione fallita, input malformato |
| `401`  | Unauthorized | Token mancante, scaduto o invalido |
| `403`  | Forbidden | Ruolo non autorizzato per questa operazione |
| `404`  | Not Found | Risorsa inesistente |
| `409`  | Conflict | Codice duplicato, vincolo violato |
| `500`  | Internal Server Error | Errore server imprevisto |

### Ruoli

| Ruolo | Descrizione |
|-------|-------------|
| `FORNITORE` | Gestisce catalogo (CRUD prodotti e SKU) |
| `CLIENTE` | Naviga catalogo, crea/gestisce configurazioni |
| `PUBBLICO` | Solo login (nessun token richiesto) |

---

## 1. Autenticazione

### `POST /api/auth/login`

> **Ruolo**: PUBBLICO

Autentica l'utente e restituisce un JWT.

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
    "token": "eyJhbGciOiJIUzI1NiIs...",
    "ruolo": "CLIENTE",
    "nome": "Mario",
    "cognome": "Rossi"
  }
}
```

**Response `401 Unauthorized`:**
```json
{
  "errore": "Credenziali non valide",
  "codice": "AUTH_INVALID_CREDENTIALS"
}
```

---

## 2. Prodotti

### `GET /api/prodotti`

> **Ruolo**: FORNITORE, CLIENTE

Restituisce la lista di tutti i prodotti radice (primo livello, `id_padre IS NULL`).
Per il CLIENTE: solo prodotti COMPOSTI, paginati.

**Query Parameters (CLIENTE):**

| Parametro | Tipo | Default | Descrizione |
|-----------|------|---------|-------------|
| `pagina`  | int  | 1       | Numero pagina (1-indexed) |
| `perPagina` | int | 10    | Elementi per pagina |

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

Restituisce il dettaglio di un prodotto con il suo albero gerarchico completo (figli ricorsivi + SKU per i prodotti semplici).

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
    "tipo": "COMPOSTO",
    "messaggio": "Prodotto creato con successo"
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

### `POST /api/prodotti/{codice}/figli`

> **Ruolo**: FORNITORE

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

**Response `400 Bad Request` (profondità):**
```json
{
  "errore": "Profondità massima (4 livelli) superata",
  "codice": "MAX_DEPTH_EXCEEDED"
}
```

**Response `409 Conflict` (ciclo):**
```json
{
  "errore": "Aggiunta bloccata: creerebbe un ciclo nella gerarchia",
  "codice": "CYCLE_DETECTED"
}
```

---

### `DELETE /api/prodotti/{codice}/figli/{figlioCodice}`

> **Ruolo**: FORNITORE

Rimuove la relazione padre-figlio (il figlio resta nel DB come radice).

**Response `200 OK`:**
```json
{
  "data": { "messaggio": "Relazione rimossa" }
}
```

---

### `POST /api/prodotti/{codice}/sku`

> **Ruolo**: FORNITORE

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
    "prezzo": 329.00,
    "messaggio": "SKU creata con successo"
  }
}
```

---

### `GET /api/sku`

> **Ruolo**: FORNITORE

Lista tutte le SKU (ordinamento per codice decrescente, per le checkbox di associazione).

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

Aggiorna uno o più attributi di una SKU (edit inline nella SPA).

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

Lista tutte le configurazioni del cliente autenticato, ordinate per data creazione decrescente.

**Response `200 OK`:**
```json
{
  "data": [
    {
      "id": 1,
      "nome": "Il mio PC Gaming",
      "dataCreazione": "2026-05-02T14:30:00",
      "stato": "CONFERMATA",
      "prezzoTotale": 1547.80
    },
    {
      "id": 3,
      "nome": "PC Ufficio (bozza)",
      "dataCreazione": "2026-05-01T09:15:00",
      "stato": "BOZZA",
      "prezzoTotale": 0.00
    }
  ]
}
```

---

### `POST /api/configurazioni`

> **Ruolo**: CLIENTE

Salva una nuova configurazione. Il backend calcola il `prezzoTotale` sommando i prezzi correnti delle SKU selezionate e li congela in `configurazione_dettaglio`.

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
    "stato": "CONFERMATA",
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

**Response `400 Bad Request` (SKU non valida):**
```json
{
  "errore": "La SKU 99 non è associata al prodotto 'CPU01'",
  "codice": "SKU_NON_ASSOCIATA"
}
```

---

### `GET /api/configurazioni/{id}`

> **Ruolo**: CLIENTE

Dettaglio di una configurazione con tutte le SKU selezionate e i prezzi congelati.

**Response `200 OK`:**
```json
{
  "data": {
    "id": 1,
    "nome": "Il mio PC Gaming",
    "dataCreazione": "2026-05-02T14:30:00",
    "stato": "CONFERMATA",
    "prezzoTotale": 1547.80,
    "dettagli": [
      {
        "skuId": 1,
        "skuCodice": 1001,
        "skuNome": "Fractal Design North",
        "skuFotografia": "/img/sku/fractal-north.jpg",
        "prezzoCongelato": 129.90
      },
      {
        "skuId": 5,
        "skuCodice": 2001,
        "skuNome": "AMD Ryzen 7 7700X",
        "skuFotografia": "/img/sku/ryzen7-7700x.jpg",
        "prezzoCongelato": 329.00
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

Modifica le selezioni SKU di una configurazione esistente (solo se in stato BOZZA).

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

Cancella una configurazione (e tutti i suoi dettagli via CASCADE).

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

Clona una configurazione esistente. Crea una nuova riga in `configurazione` con:
- Nuovo ID auto-generated
- Nome: `"Copia di {nome_originale}"`
- Data: CURRENT_TIMESTAMP
- Stesse selezioni SKU con gli stessi prezzi congelati dell'originale

**Response `201 Created`:**
```json
{
  "data": {
    "id": 8,
    "nome": "Copia di Il mio PC Gaming",
    "dataCreazione": "2026-05-02T17:00:00",
    "stato": "BOZZA",
    "prezzoTotale": 1547.80,
    "messaggio": "Configurazione clonata con successo"
  }
}
```

---

## 6. Eliminazione Ricorsiva Prodotto

### `DELETE /api/prodotti/{codice}`

> **Ruolo**: FORNITORE

Elimina un prodotto e tutta la sua sottogerarchia (CASCADE nel DB).
Le SKU vengono eliminate solo se non associate ad altri prodotti.

**Response `200 OK`:**
```json
{
  "data": {
    "messaggio": "Prodotto e sotto-gerarchia eliminati",
    "prodottiRimossi": 5,
    "skuRimosse": 3,
    "skuPreservate": 2
  }
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

## Riepilogo Endpoint

| # | Metodo | URI | Ruolo |
|---|--------|-----|-------|
| 1 | `POST` | `/api/auth/login` | PUBBLICO |
| 2 | `GET` | `/api/prodotti` | FORNITORE, CLIENTE |
| 3 | `GET` | `/api/prodotti/{codice}` | FORNITORE, CLIENTE |
| 4 | `POST` | `/api/prodotti` | FORNITORE |
| 5 | `DELETE` | `/api/prodotti/{codice}` | FORNITORE |
| 6 | `POST` | `/api/prodotti/{codice}/figli` | FORNITORE |
| 7 | `DELETE` | `/api/prodotti/{codice}/figli/{figlioCodice}` | FORNITORE |
| 8 | `POST` | `/api/prodotti/{codice}/sku` | FORNITORE |
| 9 | `DELETE` | `/api/prodotti/{codice}/sku/{skuId}` | FORNITORE |
| 10 | `POST` | `/api/sku` | FORNITORE |
| 11 | `GET` | `/api/sku` | FORNITORE |
| 12 | `PATCH` | `/api/sku/{id}` | FORNITORE |
| 13 | `DELETE` | `/api/sku/{id}` | FORNITORE |
| 14 | `GET` | `/api/ricerca?q=...` | FORNITORE |
| 15 | `GET` | `/api/configurazioni` | CLIENTE |
| 16 | `POST` | `/api/configurazioni` | CLIENTE |
| 17 | `GET` | `/api/configurazioni/{id}` | CLIENTE |
| 18 | `PUT` | `/api/configurazioni/{id}` | CLIENTE |
| 19 | `DELETE` | `/api/configurazioni/{id}` | CLIENTE |
| 20 | `POST` | `/api/configurazioni/{id}/clona` | CLIENTE |

---

*Documento generato: 2026-05-02 — Questa è la Bibbia. Non si cambia senza code review.*
