# Contratto Architetturale — TIW Configuratore di Prodotto

> **Scopo**: Questo documento stabilisce le decisioni tecniche e architetturali concordate prima di scrivere codice. Ogni scelta qui fissa un "contratto" che entrambi i membri del gruppo devono rispettare.
>
> **Stato**: Aggiornato al 2026-05-05 per riflettere le decisioni già prese e il codice già presente nel repository.

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
12. [Decisioni Aperte](#12-decisioni-aperte)
13. [Checklist Pre-Coding](#13-checklist-pre-coding)

---

## 1. Stack Tecnologico

| Layer | Tecnologia | Stato |
|---|---|---|
| **Servlet Container** | Apache Tomcat 10.1 | ✅ Configurato in `Servers/` |
| **Backend** | Java 17 + Jakarta Servlet API 6.0 | ✅ — namespace `jakarta.*` (non `javax.*`) |
| **Database** | MySQL 8+ | ✅ Schema in `database/schema.sql` |
| **Template Engine** | **Thymeleaf 3.1.2** | ✅ Deciso — dipendenza nel `tiw-ssr/pom.xml` |
| **AJAX (versione JS)** | Fetch API + `async/await` | — da implementare nel frontend |
| **Frontend** | HTML5 + CSS + JavaScript ES6+ | — nessun framework esterno |
| **Build** | **Maven multi-module** | ✅ Deciso — `pom.xml` radice con 3 moduli |
| **Connection Pooling** | JNDI DataSource su Tomcat (`context.xml`) | 🔧 Da configurare (Fase 1, collega) |
| **Hashing password** | **BCrypt (jbcrypt)** | ✅ Deciso — da aggiungere al `pom.xml` |

### Dipendenze Maven (centralizzate nel POM padre)

```xml
<!-- pom.xml radice — dependencyManagement -->
jakarta.servlet-api   6.0.0   (provided)
mysql-connector-j     8.3.0   (runtime)
jackson-databind      2.17.0
jackson-datatype-jsr310 2.17.0
thymeleaf             3.1.2.RELEASE
mindrot-jbcrypt       0.4     (da aggiungere per PasswordUtils)
tiw-core              1.0-SNAPSHOT
```

---

## 2. Schema del Database

### 2.1 Principi guida

- **Gerarchia come Adjacency List**: ogni prodotto ha un `id_padre` INT nullable (FK autoreferenziale). Con profondità max 4 e MySQL 8+ (CTE ricorsive), è la scelta più semplice. ✅ **Deciso**
- **Discriminatore di tipo**: un unico campo `tipo ENUM('SEMPLICE','COMPOSTO')` nella tabella `prodotto` evita JOIN su tabelle separate. ✅ **Deciso**
- **Surrogate key INT**: `prodotto.id` è la PK (INT AUTO_INCREMENT); `codice` è VARCHAR UNIQUE, usato come identificatore business nei form/URL. ✅ **Deciso** (lo schema reale usa `id`, non `codice` come PK)
- **Price Snapshotting**: `configurazione_dettaglio.prezzo_unitario_congelato` congela il prezzo al momento del salvataggio. ✅ **Deciso**

### 2.2 Schema definitivo

Lo schema completo e aggiornato si trova in **`database/schema.sql`**. Di seguito la struttura essenziale:

```
utente                    ← autenticazione e ruoli
prodotto                  ← catalogo (SEMPLICE + COMPOSTO, adjacency list)
sku                       ← realizzazioni concrete di un prodotto semplice
prodotto_sku              ← N:M fra prodotto e sku
configurazione            ← "scontrino" del cliente
configurazione_dettaglio  ← righe dello scontrino (con prezzo congelato)
```

Differenze rispetto alla bozza originale:
- `prodotto`: PK è `id INT AUTO_INCREMENT`, non `codice`. Il riferimento padre usa `id_padre INT` (non `parent_codice VARCHAR`).
- `configurazione_dettaglio`: si chiama così nello schema reale (non `configurazione_sku`), e include `prezzo_unitario_congelato`.
- Tutti i `CASCADE` e i `CHECK` constraint sono già presenti nello script.

### 2.3 Vincoli garantiti a livello applicativo

| Vincolo | Dove verificare |
|---|---|
| Profondità massima 4 livelli | `ProdottoDAO.calcolaLivello()` prima di INSERT |
| Assenza di cicli | `ProdottoDAO.verificaAciclicita()` risalendo gli antenati |
| Almeno una SKU per prodotto semplice | DAO/Servlet prima di SAVE e prima di configurare |
| Una SKU per ogni prodotto semplice per configurazione | `ConfigurazioneDAO` prima di INSERT (PK composita lo garantisce anche a DB) |

---

## 3. Architettura Applicativa (MVC)

### 3.1 Pattern MVC con Servlet

```
Browser
  │
  ▼
[CsrfFilter]            ← CSRF token: inietta su GET, valida su POST/PUT/DELETE
  │
  ▼
[AccessControlFilter]   ← controlla sessione; redirect /login se assente
  │
  ▼
[RoleFilter]            ← controlla ruolo vs path (/fornitore/* vs /cliente/*)
  │
  ▼
[Servlet Controller]  ← valida input, chiama DAO, prepara model / serializza JSON
  │         │
  │         ▼
  │     [DAO Layer]  ← SOLO SQL, zero logica di business
  │         │
  │         ▼
  │     [MySQL DB]
  │
  ▼
[View: Thymeleaf]  ← SOLO presentazione (tiw-ssr)
[JSON response]    ← Jackson serializza (tiw-spa)
```

### 3.2 Package Java

```
it.polimi.tiw.              ← modulo tiw-core (JAR condiviso)
    ├── model/              ← POJO (Utente, Prodotto, SKU, Configurazione)
    ├── dao/                ← UtenteDAO, ProdottoDAO, SKUDAO, ConfigurazioneDAO
    ├── filter/             ← CsrfFilter ✅, AccessControlFilter ✅, RoleFilter ✅
    ├── dto/                ← UtenteSessionDTO ✅ (record immutabile)
    └── utils/              ← ConnectionFactory 🔧, PasswordUtils 🔧

it.polimi.tiw.servlet.web.  ← modulo tiw-ssr (WAR Thymeleaf)
it.polimi.tiw.servlet.api.  ← modulo tiw-spa (WAR JSON API)
```

### 3.3 Regola fondamentale MVC

- **DAO**: non sa nulla di HTTP, Session, request/response. Riceve `Connection` via costruttore.
- **View** (Thymeleaf): solo condizionali di presentazione, nessuna logica di business.
- **Servlet**: non scrive SQL — chiama solo metodi DAO. Non si fida dell'input del client.

---

## 4. Struttura del Progetto

✅ **Deciso**: Maven multi-module. Il codice condiviso non viene copiato — vive in `tiw-core` come JAR.

```
tiw-parent/                    ← POM padre (packaging=pom, no artefatto)
├── pom.xml                    ← versioni centralizzate, plugin config
│
├── tiw-core/                  ← JAR condiviso da entrambi i WAR
│   └── src/main/java/it/polimi/tiw/
│       ├── model/             ← POJO
│       ├── dao/               ← Data Access Objects
│       ├── filter/            ← CsrfFilter, AccessControlFilter, RoleFilter
│       ├── dto/               ← UtenteSessionDTO
│       └── utils/             ← ConnectionFactory, PasswordUtils
│
├── tiw-ssr/                   ← WAR versione Thymeleaf (SSR)
│   └── src/main/
│       ├── java/it/polimi/tiw/servlet/web/   ← controller HTTP → HTML
│       └── webapp/WEB-INF/templates/         ← template Thymeleaf
│
├── tiw-spa/                   ← WAR versione SPA (JSON API)
│   └── src/main/java/it/polimi/tiw/servlet/api/  ← controller HTTP → JSON
│
├── database/
│   ├── schema.sql             ← DDL definitivo MySQL 8+
│   └── data_test.sql          ← dati di test
│
└── Servers/                   ← config Tomcat Eclipse (context.xml, server.xml)
```

**Build**: `mvn clean package` dalla radice. Produce:
- `tiw-core/target/tiw-core-1.0-SNAPSHOT.jar`
- `tiw-ssr/target/tiw-ssr.war`
- `tiw-spa/target/tiw-spa.war`

---

## 5. Convenzioni di Naming

### 5.1 Java

| Elemento | Convenzione | Esempio |
|---|---|---|
| Package | lowercase, dot-separated | `it.polimi.tiw.dao` |
| Classe Model | PascalCase | `Prodotto`, `SKU`, `Configurazione` |
| Classe DAO | PascalCase + "DAO" | `ProdottoDAO`, `SKUDAO` |
| Classe Servlet (SSR) | PascalCase + "Controller" | `WebProdottoController`, `LoginServlet` |
| Classe Servlet (SPA) | "Api" + PascalCase + "Controller" | `ApiProdottoController` |
| Classe Filter | PascalCase + "Filter" | `AccessControlFilter`, `CsrfFilter`, `RoleFilter` |
| Metodi DAO | verbo + sostantivo | `findById()`, `insert()`, `findAllRoot()` |
| Costanti | UPPER_SNAKE_CASE | `SESSION_KEY = "utente"` |

### 5.2 Database

| Elemento | Convenzione | Esempio |
|---|---|---|
| Tabelle | snake_case, singolare | `prodotto`, `sku`, `configurazione` |
| Colonne | snake_case | `id_padre`, `prezzo_min` |
| FK | `fk_tabella_campo` | `fk_cfg_cliente`, `fk_ps_sku` |
| Indici | `idx_tabella_campo` | `idx_cfg_cliente`, `idx_prodotto_nome` |

### 5.3 URL

| Elemento | Convenzione | Esempio |
|---|---|---|
| Pagine HTML | kebab-case | `/fornitore/home`, `/cliente/scelta-sku` |
| Endpoint JSON | kebab-case, sostantivo plurale | `/api/prodotti`, `/api/configurazioni` |
| Parametri query | camelCase | `?prodottoCodice=PC001` |

### 5.4 Sessione HTTP

La chiave di sessione per l'utente loggato è centralizzata in `UtenteSessionDTO.SESSION_KEY = "utente"`.
Non usare la classe `SessionConstants` (soppressa — era ridondante con il DTO).

```java
// Scrittura (LoginServlet):
session.setAttribute(UtenteSessionDTO.SESSION_KEY, new UtenteSessionDTO(...));

// Lettura (Filter, Servlet):
UtenteSessionDTO utente = (UtenteSessionDTO) session.getAttribute(UtenteSessionDTO.SESSION_KEY);
```

---

## 6. URL Mapping e Routing

### 6.1 Versione HTML (tiw-ssr)

| URL | Metodo | Servlet | Descrizione |
|---|---|---|---|
| `/login` | GET | `LoginServlet` | Mostra pagina login |
| `/login` | POST | `LoginServlet` | Processa credenziali |
| `/logout` | GET | `LogoutServlet` | Invalida sessione, redirect a login |
| `/fornitore/home` | GET | `HomeFornitoreServlet` | Home fornitore con 3 form |
| `/fornitore/home` | POST | `HomeFornitoreServlet` | Crea SKU / Prodotto |
| `/fornitore/ricerca` | GET | `RicercaServlet` | Pagina ricerca |
| `/fornitore/ricerca` | POST | `RicercaServlet` | Esegue ricerca |
| `/fornitore/rimuovi` | POST | `RimuoviServlet` | Elimina relazione o oggetto |
| `/cliente/home` | GET | `HomeClienteServlet` | Lista prodotti composti (paginazione) |
| `/cliente/scelta-sku` | GET | `SceltaSkuServlet` | Pagina configurazione |
| `/cliente/scelta-sku` | POST | `SceltaSkuServlet` | Salva configurazione |
| `/cliente/dettaglio` | GET | `DettaglioServlet` | Dettaglio configurazione |
| `/cliente/configurazioni` | GET | `MieConfigurazioniServlet` | Lista configurazioni |
| `/cliente/configurazioni` | POST | `MieConfigurazioniServlet` | Cancella / Clona / Modifica |

### 6.2 Versione JavaScript (tiw-spa)

Tutti gli endpoint usano il prefisso `/api/`. Restituiscono sempre `Content-Type: application/json; charset=UTF-8`.

| URL | Metodo | Descrizione |
|---|---|---|
| `/api/login` | POST | Autenticazione — esclusa da CSRF |
| `/api/logout` | POST | Invalida sessione |
| `/api/prodotti` | GET | Lista prodotti radice |
| `/api/prodotti` | POST | Crea prodotto |
| `/api/prodotti/{codice}` | GET | Dettaglio con albero ricorsivo |
| `/api/prodotti/{codice}/figli` | POST | Aggiungi sottoprodotto |
| `/api/prodotti/{codice}/figli/{figlio}` | DELETE | Rimuovi relazione padre-figlio |
| `/api/prodotti/{codice}/sku` | POST | Aggiungi SKU a prodotto semplice |
| `/api/prodotti/{codice}/sku/{id}` | DELETE | Rimuovi SKU da prodotto semplice |
| `/api/ricerca` | GET | `?q=termine` — ricerca prodotti + SKU |
| `/api/configurazioni` | GET | Lista configurazioni del cliente |
| `/api/configurazioni` | POST | Crea nuova configurazione |
| `/api/configurazioni/{id}` | DELETE | Cancella configurazione |
| `/api/configurazioni/{id}/clona` | POST | Clona configurazione |

---

## 7. Gestione della Sessione e Autenticazione

### 7.1 Cosa va in sessione

```java
// Solo il DTO leggero — MAI il POJO Utente (contiene password_hash)
UtenteSessionDTO dto = new UtenteSessionDTO(
    utente.getUsername(), utente.getNome(), utente.getCognome(), utente.getRuolo()
);
session.setAttribute(UtenteSessionDTO.SESSION_KEY, dto);
```

Il record `UtenteSessionDTO` è immutabile per costruzione (Java `record`): non ha setter, quindi non è possibile scalare i privilegi a runtime con `setRuolo()`. Contiene solo `username`, `nome`, `cognome`, `ruolo`.

### 7.2 CSRF Token

✅ **Implementato** in `CsrfFilter`. Il token **non va generato nella LoginServlet**: il filtro lo inietta automaticamente in sessione al primo GET post-login.

- **Generazione**: 32 byte di `SecureRandom`, Base64-url-encoded. Salvato in sessione come `"csrfToken"`.
- **Distribuzione SSR**: il template Thymeleaf deve includere `<input type="hidden" name="_csrf" th:value="${session.csrfToken}">` in ogni form mutante.
- **Distribuzione SPA**: ogni chiamata `fetch()` POST/PUT/DELETE deve includere l'header `X-CSRF-Token: <valore>`. Il token va letto dalla sessione tramite un endpoint GET iniziale o da un meta-tag nella pagina base.
- **Validazione**: confronto constant-time (`MessageDigest.isEqual`) nel filtro su ogni richiesta mutante non in whitelist.

### 7.3 Access Control Filter ✅

✅ **Implementato** in `AccessControlFilter`. Mappato su `/*` (eseguito dopo `CsrfFilter`).

Path pubblici (pass-through senza sessione):
- `/login`
- `/static/*`
- qualsiasi path che termina con `/index.html`

Logica:

```
1. Calcola relativePath = URI − contextPath
2. isPublic = PUBLIC_PATHS.contains(relativePath)
             || relativePath.startsWith("/static/")
             || relativePath.endsWith("/index.html")
3. isAuthenticated = sessione presente
                     && sessione contiene UtenteSessionDTO
4. Se isAuthenticated || isPublic → chain.doFilter()
5. Altrimenti → redirect /login
```

Il controllo del ruolo è delegato al `RoleFilter` (eseguito subito dopo nella chain).

### 7.4 Role Filter ✅

✅ **Implementato** in `RoleFilter`. Mappato su `/*` (eseguito dopo `AccessControlFilter`).

Agisce solo su path con prefisso ruolo-specifico:
- `/fornitore/*` e `/api/fornitore/*` → richiede `utente.isFornitore()`
- `/cliente/*` e `/api/cliente/*` → richiede `utente.isCliente()`
- Tutti gli altri path (es. `/login`, `/static/*`) → pass-through senza controllo

```
1. Se path non è area fornitore né area cliente → chain.doFilter()
2. Recupera UtenteSessionDTO dalla sessione (AccessControlFilter garantisce che esista)
3. Se DTO assente (sessione scaduta tra i due filtri) → redirect /login
4. Se ruolo non corrisponde all'area → 403 (JSON per /api/*, redirect /login per SSR)
5. Altrimenti → chain.doFilter()
```

I Servlet verificano il ruolo **anche internamente** (difesa in profondità — non fidarsi solo del filtro).

---

## 8. Gestione degli Errori e Validazione

### 8.1 Doppia validazione

Ogni input va validato due volte:
1. **Client-side** (HTML5 `required`, `min`, `pattern` o JavaScript): UX immediata
2. **Server-side** (Servlet prima di chiamare il DAO): sicurezza — non ci si fida del client mai

### 8.2 Errori form (versione HTML)

Quando un POST fallisce la validazione server-side: usare `forward` (non redirect) alla stessa pagina.

```java
request.setAttribute("errori", listaErrori);
request.setAttribute("valoriForm", mappaValori);  // per ripopolare i campi
request.getRequestDispatcher("/WEB-INF/templates/fornitore/home.html").forward(request, response);
```

### 8.3 Errori JSON (versione SPA)

```
400 Bad Request   → validazione fallita         { "errore": "messaggio" }
401 Unauthorized  → sessione assente             { "errore": "Non autenticato" }
403 Forbidden     → ruolo errato o CSRF fallito  { "errore": "Accesso negato" }
404 Not Found     → risorsa non trovata          { "errore": "Non trovato" }
500 Server Error  → eccezione non gestita        { "errore": "Errore interno" }
```

### 8.4 Autorizzazione sulle risorse

Ogni Servlet che riceve un `id` o `codice` dal client deve verificare che **l'utente loggato abbia il diritto di operare su quella risorsa** (es: un cliente non può leggere configurazioni di un altro cliente). Non basta che la risorsa esista — deve essere di proprietà dell'utente corrente.

---

## 9. Versione JS — Contratto API REST-Like

### 9.1 Formato risposta standard

```json
// Successo
{ "data": { ... } }
{ "data": [ ... ] }

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
        { "id": 1, "nome": "Fractal Design", "prezzo": 89.99 }
      ]
    }
  ]
}
```

### 9.3 Stato sul client (SPA)

L'albero del prodotto viene caricato una volta e mantenuto in memoria JavaScript. Le operazioni di modifica aggiornano:
1. Il server via `fetch` (fonte di verità)
2. L'oggetto JS locale (per aggiornare la UI senza ricaricare tutto l'albero)

---

## 10. Vincoli Critici del Dominio

### 10.1 Verifica aciclicità

Prima di aggiungere B come figlio di A: verificare che A non sia già discendente di B.

```sql
WITH RECURSIVE antenati AS (
    SELECT id, id_padre, 1 AS livello
    FROM prodotto WHERE id = ?          -- A
    UNION ALL
    SELECT p.id, p.id_padre, a.livello + 1
    FROM prodotto p JOIN antenati a ON p.id = a.id_padre
    WHERE a.livello < 4
)
SELECT id FROM antenati;
-- Se B compare nell'insieme → ciclo → blocca
```

### 10.2 Verifica profondità massima (4 livelli)

Prima di inserire B come figlio di A, calcolare il livello di A (quante FK risalendo fino alla radice). Se il livello di A è già 4, bloccare. Se è 3, B può essere inserito ma non potrà avere figli.

### 10.3 Paginazione cliente

La home cliente mostra 10 prodotti per pagina, ordinati per nome decrescente.

```sql
SELECT * FROM prodotto
WHERE tipo = 'COMPOSTO' AND id_padre IS NULL
ORDER BY nome DESC
LIMIT ? OFFSET ?;
```

Passare alla view: `paginaCorrente`, `totalePagine`, `hasPrecedente`, `hasSuccessiva`.

### 10.4 Price Snapshotting

Al salvataggio della configurazione, copiare il prezzo corrente di ogni SKU selezionata in `configurazione_dettaglio.prezzo_unitario_congelato`. Non ricalcolarlo mai a posteriori.

### 10.5 Clone configurazione

Nuova riga in `configurazione` con stesso `prodotto_radice_id`, nome "Copia di X", data attuale, e stesse righe in `configurazione_dettaglio` (stesso prezzo congelato originale — non ricampionare dal catalogo).

---

## 11. Divisione del Lavoro

| Area | Responsabile | Stato |
|---|---|---|
| Maven multi-module + POM | Collega | ✅ |
| Schema DB (`schema.sql`) | Collega | ✅ |
| `CsrfFilter` | Collega | ✅ |
| `UtenteSessionDTO` | Collega | ✅ |
| `ProdottoDAO` (base) | Collega | ✅ parziale |
| `ConnectionFactory` + `context.xml` | Collega | 🔧 in corso |
| `PasswordUtils` (BCrypt) | — | 🔧 da fare |
| `UtenteDAO` | — | 🔧 da fare |
| `AccessControlFilter` | Collega | ✅ |
| `RoleFilter` | Collega | ✅ |
| `LoginServlet` (entrambi i WAR) | — | 🔧 da fare |
| Servlet fornitore (HTML) | — | 🔧 da fare |
| Servlet cliente (HTML) | — | 🔧 da fare |
| Template Thymeleaf | — | 🔧 da fare |
| Endpoint JSON SPA | — | 🔧 da fare |
| Frontend JS SPA | — | 🔧 da fare |
| `data_test.sql` | — | 🔧 da fare |

### DAO da implementare (priorità)

1. `UtenteDAO` — `findByUsername()`, `checkCredentials()`
2. `ProdottoDAO` — `findById()`, `insert()`, `findAllRoot()`, `findChildren()`, `calcolaLivello()`, `verificaAciclicita()`, `addChild()`, `removeChild()`, `search()`
3. `SKUDAO` — `findById()`, `insert()`, `findBySemplice()`, `addToSemplice()`, `removeFromSemplice()`
4. `ConfigurazioneDAO` — `insert()`, `findByCliente()`, `findById()`, `delete()`, `clone()`

---

## 12. Decisioni Aperte

Tutte le decisioni originariamente aperte sono state risolte:

| # | Decisione | Scelta | Note |
|---|---|---|---|
| 1 | Template engine | **Thymeleaf 3.1.2** | Dipendenza presente in `tiw-ssr/pom.xml` |
| 2 | Build system | **Maven multi-module** | `pom.xml` radice funzionante |
| 3 | Codice condiviso | **Modulo `tiw-core` (JAR)** | Nessuna copia di file |
| 4 | Password hashing | **BCrypt (jbcrypt)** | Da aggiungere al `pom.xml` |
| 5 | Tipo chiave prodotto | **INT surrogate (`id`) + VARCHAR `codice` UNIQUE** | Schema reale usa `id INT AUTO_INCREMENT` come PK |
| 6 | Upload foto SKU | **Path/URL fornito dall'utente** | Nessun multipart, per semplicità accademica |
| 7 | MySQL CTE ricorsive | **MySQL 8+** | Schema usa `WITH RECURSIVE` |

---

## 13. Checklist Pre-Coding

- [x] Decisioni aperte tutte risolte
- [x] Schema DB concordato e scritto (`database/schema.sql`)
- [x] Struttura Maven multi-module creata e funzionante
- [x] `CsrfFilter` implementato
- [x] `UtenteSessionDTO` implementato
- [ ] `ConnectionFactory` (JNDI) funzionante — in corso (collega)
- [ ] `PasswordUtils` (BCrypt) implementata
- [ ] `UtenteDAO.checkCredentials()` implementato
- [x] `AccessControlFilter` implementato
- [x] `RoleFilter` implementato
- [ ] `LoginServlet` end-to-end funzionante (SSR + SPA)
- [ ] Database popolato con `data_test.sql`

---

*Documento aggiornato: 2026-05-05*
