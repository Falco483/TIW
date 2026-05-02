# Contratto Architetturale — TIW Configuratore di Prodotto

> **Scopo**: Questo documento stabilisce le decisioni tecniche e architetturali da concordare **prima** di scrivere una riga di codice. Ogni scelta qui fissa un "contratto" che entrambi i membri del gruppo devono rispettare. Modificare questi contratti in corso d'opera è costoso e fonte di conflitti.
>
> **Come usarlo**: Leggete ogni sezione, discutete i punti aperti (marcati ⚠️), e scrivete la decisione finale. Una volta concordato, questo diventa la vostra Costituzione del progetto.

---

## Indice

1. [Stack Tecnologico](#1-stack-tecnologico)
2. [Schema del Database](#2-schema-del-database)
3. [Architettura Applicativa (MVC)](#3-architettura-applicativa-mvc)
4. [Struttura del Progetto](#4-struttura-del-progetto)
5. [Convenzioni di Naming](#5-convenzioni-di-naming)
6. [URL Mapping e Routing](#6-url-mapping-e-routing)
7. [Gestione della Sessione e Autenticazione](#7-gestione-della-sessione-e-autenticazione)
8. [Gestione degli Errori e Validazione](#8-gestione-degli-errori-e-validazione)
9. [Versione JS — Contratto API REST-Like](#9-versione-js--contratto-api-rest-like)
10. [Vincoli Critici del Dominio](#10-vincoli-critici-del-dominio)
11. [Divisione del Lavoro](#11-divisione-del-lavoro)
12. [Decisioni Aperte ⚠️](#12-decisioni-aperte)
13. [Checklist Pre-Coding](#13-checklist-pre-coding)

---

## 1. Stack Tecnologico

Queste tecnologie sono **imposte dal corso** e non sono negoziabili.

| Layer | Tecnologia | Note |
|---|---|---|
| **Servlet Container** | Apache Tomcat 10.1 | Jakarta EE 9+, namespace `jakarta.*` (non più `javax.*`) |
| **Backend** | Java + Jakarta Servlet API | HttpServlet, doGet/doPost |
| **Database** | MySQL | JDBC con PreparedStatement obbligatorio (no Statement grezzo) |
| **Template Engine** | ⚠️ Thymeleaf **oppure** JSP+JSTL | Vedere sezione 12 |
| **AJAX (versione JS)** | Fetch API o XMLHttpRequest | Preferire `fetch` + `async/await` per leggibilità |
| **Frontend** | HTML5 + CSS + JavaScript ES6+ | Nessun framework JS esterno (React, Vue, ecc.) |
| **Build** | Eclipse + WAR manuale **oppure** Maven | ⚠️ Vedere sezione 12 |
| **Connection Pooling** | JNDI DataSource su Tomcat (context.xml) | Non usare DriverManager direttamente in produzione |

### Dipendenze JAR minime (da includere in WEB-INF/lib o pom.xml)

```
- mysql-connector-j-*.jar          (JDBC driver MySQL)
- thymeleaf-*.jar                  (se si usa Thymeleaf)
                   oppure
- jakarta.servlet.jsp.jstl-*.jar   (se si usa JSTL)
- jstl-*.jar
```

---

## 2. Schema del Database

Questa è la decisione **più critica** del progetto. Un schema sbagliato obbliga a riscrivere tutti i DAO.

### 2.1 Principi guida

- **Gerarchia come Adjacency List**: ogni prodotto ha un `parent_id` nullable. Con il vincolo di profondità massima 4 livelli e unicità del padre, l'adjacency list è la scelta più semplice e corretta.
- **Discriminatore di tipo**: un unico campo `tipo` nella tabella `prodotto` (`SEMPLICE` | `COMPOSTO`) evita JOIN complessi tra tabelle separate. È il pattern preferito quando le entità hanno struttura simile.
- **Unicità del padre**: garantita da un `UNIQUE KEY` sulla colonna `parent_id` — un figlio può apparire al massimo una volta come figlio.

### 2.2 Schema Proposto

```sql
-- ============================================================
-- UTENTI
-- ============================================================
CREATE TABLE utente (
    username       VARCHAR(50)  PRIMARY KEY,
    password_hash  VARCHAR(255) NOT NULL,       -- MAI plaintext
    nome           VARCHAR(100) NOT NULL,
    cognome        VARCHAR(100) NOT NULL,
    ruolo          ENUM('FORNITORE', 'CLIENTE') NOT NULL
);

-- ============================================================
-- PRODOTTI (Semplici e Composti in un'unica tabella)
-- ============================================================
CREATE TABLE prodotto (
    codice       VARCHAR(50)    PRIMARY KEY,
    nome         VARCHAR(200)   NOT NULL,
    tipo         ENUM('SEMPLICE', 'COMPOSTO') NOT NULL,

    -- Solo per COMPOSTO (NULL per SEMPLICE):
    descrizione  TEXT           NULL,
    prezzo_min   DECIMAL(10,2)  NULL CHECK (prezzo_min >= 0),
    prezzo_max   DECIMAL(10,2)  NULL CHECK (prezzo_max >= prezzo_min),

    -- Gerarchia: NULL = prodotto radice di primo livello
    parent_codice VARCHAR(50)   NULL,

    FOREIGN KEY (parent_codice) REFERENCES prodotto(codice)
        ON DELETE RESTRICT   -- non cancellare padre se ha figli
);

-- ============================================================
-- SKU
-- ============================================================
CREATE TABLE sku (
    codice              INT           PRIMARY KEY AUTO_INCREMENT,
    nome                VARCHAR(200)  NOT NULL,
    fotografia          VARCHAR(500)  NULL,       -- path relativo o URL
    descrizione_tecnica TEXT          NULL,
    prezzo              DECIMAL(10,2) NOT NULL CHECK (prezzo >= 0)
);

-- ============================================================
-- ASSOCIAZIONE SKU ↔ PRODOTTO SEMPLICE (N:M)
-- Una SKU può essere associata a più prodotti semplici
-- Un prodotto semplice deve avere almeno una SKU (check applicativo)
-- ============================================================
CREATE TABLE prodotto_sku (
    prodotto_codice VARCHAR(50) NOT NULL,
    sku_codice      INT         NOT NULL,
    PRIMARY KEY (prodotto_codice, sku_codice),
    FOREIGN KEY (prodotto_codice) REFERENCES prodotto(codice) ON DELETE CASCADE,
    FOREIGN KEY (sku_codice)      REFERENCES sku(codice)      ON DELETE CASCADE
);

-- ============================================================
-- CONFIGURAZIONI (create dai clienti)
-- ============================================================
CREATE TABLE configurazione (
    id                INT          PRIMARY KEY AUTO_INCREMENT,
    nome              VARCHAR(200) NOT NULL,
    data_creazione    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_modifica     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
                                   ON UPDATE CURRENT_TIMESTAMP,
    prezzo_totale     DECIMAL(10,2) NOT NULL CHECK (prezzo_totale >= 0),
    cliente_username  VARCHAR(50)  NOT NULL,
    prodotto_radice   VARCHAR(50)  NOT NULL,   -- prodotto composto di 1° livello
    FOREIGN KEY (cliente_username) REFERENCES utente(username),
    FOREIGN KEY (prodotto_radice)  REFERENCES prodotto(codice)
);

-- ============================================================
-- SELEZIONI NELLA CONFIGURAZIONE
-- Per ogni configurazione: quale SKU è stata scelta per ogni prodotto semplice
-- ============================================================
CREATE TABLE configurazione_sku (
    configurazione_id INT         NOT NULL,
    prodotto_codice   VARCHAR(50) NOT NULL,   -- prodotto SEMPLICE
    sku_codice        INT         NOT NULL,
    PRIMARY KEY (configurazione_id, prodotto_codice),
    FOREIGN KEY (configurazione_id) REFERENCES configurazione(id) ON DELETE CASCADE,
    FOREIGN KEY (prodotto_codice)   REFERENCES prodotto(codice),
    FOREIGN KEY (sku_codice)        REFERENCES sku(codice)
);
```

### 2.3 Vincoli garantiti a livello applicativo (non solo DB)

Questi vincoli il DB **non può** garantire da solo — vanno implementati nei DAO/Servlet:

| Vincolo | Dove verificare |
|---|---|
| Profondità massima 4 livelli | DAO `ProdottoDAO.calcolaLivello()` prima di INSERT |
| Assenza di cicli | DAO `ProdottoDAO.verificaAciclicita()` risalendo la catena di antenati |
| Almeno una SKU per prodotto semplice | Servlet/DAO prima di SAVE, e al momento del tentativo di configurazione |
| Una SKU per ogni prodotto semplice in una configurazione | Servlet `ConfigurazioneServlet` prima di INSERT |

### 2.4 Nota sulla password

Per semplicità accademica: usare `SHA-256` con `MessageDigest` Java. **Non usare MD5**. Idealmente `BCrypt` (libreria esterna), ma verificare con il docente se è ammessa.

---

## 3. Architettura Applicativa (MVC)

### 3.1 Pattern MVC con Servlet

```
Browser
  │
  ▼
[Servlet Filter]  ← gestisce autenticazione/autorizzazione su tutte le rotte
  │
  ▼
[Servlet Controller]  ← riceve request, valida input, chiama DAO, prepara model
  │         │
  │         ▼
  │     [DAO Layer]  ← SOLO SQL, nessuna logica di business
  │         │
  │         ▼
  │     [MySQL DB]
  │
  ▼
[View: Thymeleaf/JSP]  ← SOLO presentazione, nessuna logica
```

### 3.2 Package Java

```
it.polimi.tiw.
    ├── model/          ← POJO (Utente, Prodotto, SKU, Configurazione)
    ├── dao/            ← UtenteDAO, ProdottoDAO, SKUDAO, ConfigurazioneDAO
    ├── servlet/
    │   ├── html/       ← Servlet per versione HTML pura
    │   └── js/         ← Servlet per versione JS (rispondono JSON)
    ├── filter/         ← AuthenticationFilter, RoleFilter
    └── utils/          ← ConnectionFactory, PasswordUtils, ecc.
```

> **Decisione chiave**: Le due versioni (HTML e JS) condividono Model e DAO. Le Servlet sono separate perché producono output diverso (HTML vs JSON).

### 3.3 Regola fondamentale MVC

- **Model** (DAO + POJO): non sa nulla di HTTP, Session, request/response.
- **View** (JSP/Thymeleaf): non contiene logica di business, solo condizionali di presentazione.
- **Controller** (Servlet): non scrive SQL direttamente — **solo** chiama metodi DAO.

---

## 4. Struttura del Progetto

Due **WAR distinte**, due progetti separati in Eclipse/IDE.

```
tiw-html/                           tiw-js/
├── src/main/java/                  ├── src/main/java/
│   └── it/polimi/tiw/              │   └── it/polimi/tiw/
│       ├── model/                  │       ├── model/         (stesso codice)
│       ├── dao/                    │       ├── dao/           (stesso codice)
│       ├── servlet/html/           │       ├── servlet/js/
│       ├── filter/                 │       ├── filter/        (stesso codice)
│       └── utils/                  │       └── utils/         (stesso codice)
├── src/main/webapp/
│   ├── WEB-INF/
│   │   ├── web.xml
│   │   └── views/                  │   ├── WEB-INF/views/
│   │       ├── login.html          │   │   └── index.html     (SPA unica)
│   │       ├── fornitore/          │   ├── static/
│   │       │   ├── home.html       │   │   ├── js/
│   │       │   └── ricerca.html    │   │   └── css/
│   │       └── cliente/
│   │           ├── home.html
│   │           ├── sceltaSku.html
│   │           ├── dettaglio.html
│   │           └── mieConfigurazioni.html
│   └── static/
│       ├── css/
│       └── images/                 ← upload foto SKU
```

> ⚠️ **Attenzione**: il codice duplicato (model, dao, filter, utils) può essere estratto in un progetto "tiw-common" come dipendenza Maven, oppure copiato. Decidere insieme.

---

## 5. Convenzioni di Naming

Rispettare queste convenzioni evita conflitti quando si integra il codice.

### 5.1 Java

| Elemento | Convenzione | Esempio |
|---|---|---|
| Package | lowercase, dot-separated | `it.polimi.tiw.dao` |
| Classe Model | PascalCase | `Prodotto`, `SKU`, `Configurazione` |
| Classe DAO | PascalCase + "DAO" | `ProdottoDAO`, `SKUDAO` |
| Classe Servlet | PascalCase + "Servlet" | `LoginServlet`, `HomeFornitoreServlet` |
| Classe Filter | PascalCase + "Filter" | `AuthenticationFilter` |
| Metodi DAO | verbo + sostantivo | `findById()`, `insert()`, `delete()`, `findAllByParent()` |
| Costanti | UPPER_SNAKE_CASE | `SESSION_USER = "utente"` |

### 5.2 Database

| Elemento | Convenzione | Esempio |
|---|---|---|
| Tabelle | snake_case, singolare | `prodotto`, `sku`, `configurazione` |
| Colonne | snake_case | `parent_codice`, `prezzo_min` |
| FK | `tabella_riferita_campo` | `prodotto_codice`, `sku_codice` |
| Indici | `idx_tabella_campo` | `idx_configurazione_cliente` |

### 5.3 URL

| Elemento | Convenzione | Esempio |
|---|---|---|
| Pagine HTML | kebab-case | `/fornitore/home`, `/cliente/scelta-sku` |
| Endpoint JSON (JS) | kebab-case, sostantivo plurale | `/api/prodotti`, `/api/configurazioni` |
| Parametri query | camelCase | `?prodottoCodice=PC001` |

### 5.4 Variabili di Sessione (chiavi `HttpSession`)

Usare costanti condivise in una classe `SessionConstants`:

```java
public class SessionConstants {
    public static final String UTENTE        = "utente";        // oggetto Utente
    public static final String RUOLO         = "ruolo";         // "FORNITORE" | "CLIENTE"
    // (opzionale) per versione HTML: preservare form su errore
    public static final String FORM_ERRORS   = "formErrors";
    public static final String FORM_VALUES   = "formValues";
}
```

---

## 6. URL Mapping e Routing

### 6.1 Versione HTML

| URL | Metodo | Servlet | Descrizione |
|---|---|---|---|
| `/login` | GET | `LoginServlet` | Mostra pagina login |
| `/login` | POST | `LoginServlet` | Processa credenziali |
| `/logout` | GET | `LogoutServlet` | Invalida sessione, redirect a login |
| `/fornitore/home` | GET | `HomeFornitoreServlet` | Home fornitore con 3 form |
| `/fornitore/home` | POST | `HomeFornitoreServlet` | Elabora creazione SKU/Prodotto |
| `/fornitore/ricerca` | GET | `RicercaServlet` | Pagina ricerca |
| `/fornitore/ricerca` | POST | `RicercaServlet` | Esegue ricerca |
| `/fornitore/rimuovi` | POST | `RimuoviServlet` | Elimina relazione o oggetto |
| `/cliente/home` | GET | `HomeClienteServlet` | Lista prodotti composti (con paginazione) |
| `/cliente/scelta-sku` | GET | `SceltaSkuServlet` | Pagina configurazione |
| `/cliente/scelta-sku` | POST | `SceltaSkuServlet` | Salva configurazione |
| `/cliente/dettaglio` | GET | `DettaglioServlet` | Dettaglio configurazione |
| `/cliente/configurazioni` | GET | `MieConfigurazioniServlet` | Lista configurazioni |
| `/cliente/configurazioni` | POST | `MieConfigurazioniServlet` | Cancella / Clona / Modifica |

### 6.2 Versione JavaScript (endpoint API)

Tutti gli endpoint JSON usano il prefisso `/api/`. Restituiscono sempre `Content-Type: application/json`.

| URL | Metodo | Descrizione | Response |
|---|---|---|---|
| `/api/sku` | POST | Crea SKU | `{ codice, nome, ... }` |
| `/api/sku/{codice}` | PATCH | Aggiorna attributo SKU | `{ success: true }` |
| `/api/prodotti` | POST | Crea prodotto (semplice o composto) | `{ codice, ... }` |
| `/api/prodotti/{codice}` | GET | Dettaglio prodotto con albero | `{ prodotto + figli ricorsivi }` |
| `/api/prodotti/{codice}/figli` | POST | Aggiungi sottoprodotto | |
| `/api/prodotti/{codice}/figli/{figlio}` | DELETE | Rimuovi relazione padre-figlio | |
| `/api/prodotti/{codice}/sku` | POST | Aggiungi SKU a prodotto semplice | |
| `/api/prodotti/{codice}/sku/{sku}` | DELETE | Rimuovi SKU da prodotto semplice | |
| `/api/ricerca` | GET | `?q=termine` ricerca prodotti+SKU | `[ array risultati ]` |
| `/api/configurazioni` | GET | Lista configurazioni cliente | |
| `/api/configurazioni` | POST | Crea nuova configurazione | |
| `/api/configurazioni/{id}` | DELETE | Cancella configurazione | |
| `/api/configurazioni/{id}/clona` | POST | Clona configurazione | |

> **Nota**: il termine "REST" qui è improprio perché non abbiamo un'autenticazione stateless — usiamo sessione HTTP. È comunque il pattern corretto per questo progetto.

---

## 7. Gestione della Sessione e Autenticazione

### 7.1 Cosa va in sessione

```java
// Solo l'oggetto Utente (POJO leggero)
session.setAttribute(SessionConstants.UTENTE, utente); // include username, nome, cognome, ruolo
```

Non mettere in sessione: liste di prodotti, risultati di ricerca, oggetti pesanti. La sessione deve essere **minimale**.

### 7.2 Authentication Filter

Un unico `AuthenticationFilter` mappato su `/*` (o `/fornitore/*` + `/cliente/*` + `/api/*`).

```
Logica del Filter:
1. Se la richiesta è verso /login o /static/* → lascia passare (chain.doFilter)
2. Controlla session.getAttribute("utente")
3. Se null → redirect a /login
4. Se ruolo != quello richiesto dal path (/fornitore/* vs /cliente/*) → 403 o redirect
5. Altrimenti → chain.doFilter (procede normalmente)
```

### 7.3 Controllo Ruolo nei Servlet

Ogni Servlet di fornitore verifica `utente.getRuolo().equals("FORNITORE")`. Non fidarsi solo del path — **difendersi sempre server-side** anche se il filter già controlla.

---

## 8. Gestione degli Errori e Validazione

### 8.1 Regola doppia validazione

**Ogni input utente va validato due volte:**
1. **Client-side** (HTML5 `required`, `min`, `pattern`, o JavaScript): per UX immediata
2. **Server-side** (Servlet prima di chiamare DAO): per sicurezza (non ci si fida del client)

### 8.2 Strategia form con errori (versione HTML)

Quando un form POST fallisce la validazione server-side:
1. Non fare redirect (usare `forward` alla stessa pagina)
2. Inserire in `request` (non session) i valori precedentemente inseriti e i messaggi di errore
3. Il template ripopola i campi con `${param.nomeCampo}` o `${requestScope.formValues}`

```java
// Nel Servlet, in caso di errore:
request.setAttribute("errori", listaErrori);
request.setAttribute("valoriForm", mappaValori); // preserva input
RequestDispatcher dispatcher = request.getRequestDispatcher("/WEB-INF/views/fornitore/home.html");
dispatcher.forward(request, response);
```

### 8.3 Risposta JSON agli errori (versione JS)

Usare HTTP status code semantici:
- `400 Bad Request` → validazione fallita (body JSON con `{ "errore": "messaggio" }`)
- `401 Unauthorized` → non autenticato
- `403 Forbidden` → autenticato ma ruolo errato
- `404 Not Found` → risorsa non trovata
- `500 Internal Server Error` → eccezione non gestita

```java
// Helper da mettere in una classe Utils
response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
response.setContentType("application/json");
response.getWriter().write("{\"errore\": \"Codice già esistente\"}");
```

### 8.4 Sicurezza parametri

Ogni Servlet che riceve un `codice` o `id` dal client deve:
1. Verificare che il parametro esista e non sia vuoto
2. Verificare che la risorsa esista nel DB
3. **Verificare che l'utente abbia il diritto di operare su quella risorsa** (es: un cliente non può vedere configurazioni di un altro cliente)

---

## 9. Versione JS — Contratto API REST-Like

### 9.1 Formato risposta standard

Tutti gli endpoint JSON rispettano questo contratto:

```json
// Successo
{ "data": { ... } }   oppure   { "data": [ ... ] }

// Errore
{ "errore": "Messaggio leggibile dall'utente" }
```

### 9.2 Albero prodotto — formato JSON ricorsivo

```json
{
  "codice": "PC001",
  "nome": "PC Desktop",
  "tipo": "COMPOSTO",
  "descrizione": "...",
  "prezzoMin": 500.00,
  "prezzoMax": 2000.00,
  "figli": [
    {
      "codice": "CASE001",
      "nome": "Case",
      "tipo": "SEMPLICE",
      "sku": [
        { "codice": 1, "nome": "Fractal Design", "prezzo": 89.99 }
      ]
    },
    {
      "codice": "ELAB001",
      "nome": "Sistema di Elaborazione",
      "tipo": "COMPOSTO",
      "figli": [ "..." ]
    }
  ]
}
```

### 9.3 Regola SPA: stato sul client

Nella versione JS l'albero del prodotto viene caricato una volta e mantenuto in memoria JavaScript (oggetto/variabile globale). Le operazioni di modifica (aggiungi figlio, rimuovi, ecc.) aggiornano:
1. Il server via fetch (fonte di verità)
2. L'oggetto JS locale (per aggiornare la UI senza ricaricare tutto l'albero)

---

## 10. Vincoli Critici del Dominio

Questi sono i punti dove è più facile sbagliare. Richiedono implementazione attenta.

### 10.1 Verifica aciclicità

Prima di aggiungere il prodotto B come figlio di A, verificare che A non sia già discendente di B.

```
Algoritmo: risali la catena di antenati di A.
Se trovi B tra gli antenati → CICLO → blocca operazione.
```

```sql
-- Query DAO: trova tutti gli antenati di un prodotto (con profondità limitata a 4)
WITH RECURSIVE antenati AS (
    SELECT codice, parent_codice, 1 AS livello
    FROM prodotto WHERE codice = ?
    UNION ALL
    SELECT p.codice, p.parent_codice, a.livello + 1
    FROM prodotto p JOIN antenati a ON p.codice = a.parent_codice
    WHERE a.livello < 4
)
SELECT codice FROM antenati;
```

> Se MySQL supporta CTE ricorsive (MySQL 8+), usarle. Altrimenti implementare in Java con query iterative.

### 10.2 Verifica profondità massima

Prima di aggiungere B come figlio di A, calcolare il livello di A nella gerarchia:
- A a livello 1 (radice) → B può essere al massimo a livello 4 (→ B può avere figli fino a livello 4)
- A a livello 3 → B è a livello 4 → B **non può avere figli** (è forzato a essere semplice)
- A a livello 4 → non si può aggiungere figli

### 10.3 Paginazione cliente

La home cliente mostra **10 prodotti** per pagina, ordinati per nome **decrescente**.

```sql
SELECT * FROM prodotto
WHERE tipo = 'COMPOSTO' AND parent_codice IS NULL
ORDER BY nome DESC
LIMIT ? OFFSET ?;
```

Parametri da passare alla view: `paginaCorrente`, `totalePagine`, `hasPrecedente`, `hasSuccessiva`.

### 10.4 Prezzo totale configurazione

Il prezzo totale si calcola **sommando i prezzi delle SKU selezionate** al momento del salvataggio. Non va ricalcolato dinamicamente (il prezzo di una SKU potrebbe cambiare dopo).

```sql
-- Al momento del salvataggio, calcola e persisti il prezzo totale
SELECT SUM(s.prezzo) FROM sku s
JOIN configurazione_sku cs ON s.codice = cs.sku_codice
WHERE cs.configurazione_id = ?;
```

### 10.5 Clone configurazione

"Clona" significa creare una nuova riga in `configurazione` con le stesse righe in `configurazione_sku`, ma con nuovo `id`, nome modificato (es. "Copia di X"), data attuale. Il prezzo rimane lo stesso delle SKU originali.

---

## 11. Divisione del Lavoro

### Suggerimento di suddivisione (da concordare)

Il progetto si divide naturalmente in queste macro-aree:

| Area | Complessità | Suggerimento assegnazione |
|---|---|---|
| **Setup DB + DDL** | Bassa | Fare insieme (30 min) |
| **Model + DAO** | Media-Alta | Fare insieme o distribuire 50/50 |
| **Auth (Login/Logout/Filter)** | Media | Persona A |
| **Interfaccia Fornitore HTML** | Alta | Persona A |
| **Interfaccia Cliente HTML** | Alta | Persona B |
| **Endpoint JSON (versione JS)** | Media | Distribuire per ruolo (chi ha fatto HTML) |
| **Frontend JS SPA Fornitore** | Alta | Persona A |
| **Frontend JS SPA Cliente** | Alta | Persona B |
| **CSS e impaginazione** | Bassa | Distribuire |
| **Dataset di test** | Bassa | Persona B |

> **Regola**: Model e DAO vanno **concordati insieme** prima di dividersi, perché tutto il codice dipende da queste interfacce.

### DAO da implementare (priorità)

1. `UtenteDAO` — `findByUsername()`, `insert()`, `checkCredentials()`
2. `ProdottoDAO` — `findById()`, `insert()`, `findAllRoot()`, `findChildren()`, `findAllSemplici()`, `calcolaLivello()`, `verificaAciclicita()`, `addChild()`, `removeChild()`, `deleteRecursive()`, `search()`
3. `SKUDAO` — `findById()`, `insert()`, `findBySemplice()`, `addToSemplice()`, `removeFromSemplice()`, `delete()`
4. `ConfigurazioneDAO` — `insert()`, `findByCliente()`, `findById()`, `delete()`, `clone()`, `updateSelezioni()`

---

## 12. Decisioni Aperte

Questi punti **devono essere discussi e risolti prima di iniziare a scrivere codice**.

| # | Decisione | Opzione A | Opzione B | Note |
|---|---|---|---|---|
| ⚠️ 1 | **Template engine** | Thymeleaf | JSP + JSTL | Thymeleaf è moderno e raccomandato dal corso; JSP+JSTL è più comune negli esempi didattici |
| ⚠️ 2 | **Build system** | Eclipse projects + WAR manuale | Maven | Maven facilita dipendenze e build, ma richiede configurazione iniziale |
| ⚠️ 3 | **Codice condiviso** | Due progetti Eclipse separati con copia del codice | Un progetto "common" come dependency | La copia è più semplice ma crea duplicati |
| ⚠️ 4 | **Password hashing** | SHA-256 con MessageDigest | Confronto diretto (solo per demo) | Il docente potrebbe accettare confronto diretto per semplicità, ma SHA-256 è corretto |
| ⚠️ 5 | **Tipo codice prodotto** | Stringa (VARCHAR) | Intero auto-increment | Lo schema delle specifiche usa "codice" senza dire il tipo; stringa è più flessibile |
| ⚠️ 6 | **Upload foto SKU** | File caricato sul server (multipart) | Solo URL/path fornito dall'utente | Il multipart è più completo ma richiede gestione filesystem |
| ⚠️ 7 | **MySQL CTE ricorsive** | MySQL 8+ (supporta WITH RECURSIVE) | MySQL 5.7 (no CTE, serve Java) | Verificare versione MySQL del laboratorio |

---

## 13. Checklist Pre-Coding

Prima di scrivere la prima Servlet, verificare che siano stati completati:

- [ ] Decisioni aperte (sezione 12) tutte risolte e scritte qui sopra
- [ ] Schema DB concordato e file `.sql` scritto
- [ ] Ambiente di sviluppo configurato (Tomcat in Eclipse/IntelliJ, connessione MySQL)
- [ ] Struttura cartelle del progetto creata (anche vuota)
- [ ] Package Java creati con classi vuote (Model + DAO stub)
- [ ] `ConnectionFactory` (o DataSource JNDI) funzionante e testato
- [ ] Login/Logout funzionante end-to-end (il foundation di tutto)
- [ ] Database popolato con dati di test per tutti gli scenari

---

*Documento creato: 2026-05-02 — Da aggiornare ad ogni decisione concordata.*
