# CLAUDE.md (v2 — aggiornato post-rollback architetturale)

This file provides guidance when working with code in this repository.

## Project Overview

University project for "Tecnologie Informatiche per il Web" (TIW) at Politecnico di Milano.
Implements a **Product Configurator** web app in **two interface modes within the same WAR**:

- **SSR (Server-Side Rendering):** Thymeleaf templates, traditional form submits, multi-page
- **SPA (Single Page Application):** Vanilla JS + fetch API, async, single-page

Both modes share the same DAO layer and the same `HttpSession` authentication.
Deployed on **Apache Tomcat 10.1** (Jakarta EE 9+, use `jakarta.*` namespace, not `javax.*`).

## Build & Deploy

Project uses **Maven** (`TIW_project/pom.xml`). Commands:

```bash
cd TIW_project
mvn clean package        # produces target/tiw-configuratore.war
```

Drop `tiw-configuratore.war` into Tomcat's `webapps/` folder.
The `Servers/` directory contains Tomcat's `context.xml` (JNDI DataSource config for MySQL).

**Key dependencies** (managed in pom.xml):
- Jakarta Servlet API 6.0 (scope `provided` — Tomcat includes it)
- MySQL Connector/J 8.3 (scope `runtime`)
- Jackson Databind 2.17 + JSR310 module (JSON serialization)
- Thymeleaf 3.1.2 (server-side template engine)

## Architecture

MVC pattern with Servlet controllers. Package structure under `it.polimi.tiw`:

```
model/          — POJOs: Utente, Prodotto, SKU, Configurazione
dao/            — DB access ONLY: UtenteDAO, ProdottoDAO, SKUDAO, ConfigurazioneDAO
servlet/
  web/          — Controllers for Thymeleaf (forward to .html templates)
  api/          — Controllers for SPA (return JSON via Jackson ObjectMapper)
filter/         — AuthenticationFilter + CsrfFilter
utils/          — ConnectionFactory (JNDI DataSource), PasswordUtils
```

**Key constraint (DRY)**: Both `servlet/web/` and `servlet/api/` controllers call the
**same DAO methods**. The DAO is the single source of truth for data access logic.
Never duplicate SQL queries across controllers.

**Separation of Concerns**:
- **DAO**: Only SQL + ResultSet→POJO mapping. No HTTP, no Session, no JSON.
- **Controller (Servlet)**: Only orchestration. Gets connection, calls DAO, outputs result.
- **View (Thymeleaf)**: Only presentation. No business logic, no SQL.

## Database

MySQL 8+. Schema defined in `database/schema.sql`. Key tables:

| Table | Area | Notes |
|-------|------|-------|
| `utente` | Auth | PK: `username VARCHAR(50)`, `password_hash`, `ruolo ENUM` |
| `prodotto` | Catalog | Single Table Inheritance (`tipo ENUM`), Adjacency List (`id_padre INT` FK→self) |
| `sku` | Catalog | PK surrogate `id INT`, business key `codice INT UNIQUE` |
| `prodotto_sku` | Catalog | Junction N:M, PK composite `(id_prodotto, id_sku)` |
| `configurazione` | Transactional | FK `cliente_username`→utente, FK `prodotto_radice_id`→prodotto |
| `configurazione_dettaglio` | Transactional | **Price Snapshotting**: `prezzo_unitario_congelato`, PK `(id_configurazione, id_prodotto)` |

**PK strategy**: Surrogate `id INT AUTO_INCREMENT` as PK, business `codice` as UNIQUE.
All FK references use the surrogate `id`, not the business `codice`.

Use `PreparedStatement` always — never raw `Statement`.
Connection via JNDI DataSource from `context.xml`, not `DriverManager`.

## Authentication — Stateful (HttpSession)

`LoginServlet` verifies credentials against `utente` table. On success:

```java
HttpSession session = request.getSession(true);
session.setAttribute("userId", utente.getUsername());
session.setAttribute("ruolo", utente.getRuolo());
// Generate CSRF token at login time
String csrfToken = generateToken();
session.setAttribute("csrfToken", csrfToken);
```

**What goes in session**: Only `userId` (String) and `ruolo` (String).
Never store the full entity object — wastes RAM.

Tomcat sends a `JSESSIONID` cookie automatically. The browser re-sends it
on every subsequent request. No `Authorization` header needed.

`AuthenticationFilter` intercepts protected paths. It:
1. Lets `/login`, `/api/login`, and `/static/*` pass through
2. Checks `session.getAttribute("userId")` — if null → 401 or redirect to `/login`
3. Checks `session.getAttribute("ruolo")` against the requested path → 403 if mismatch
4. Servlets also independently verify the role — defense in depth

## CSRF Protection — Synchronizer Token Pattern

Since we use cookies (`JSESSIONID`), we are vulnerable to CSRF attacks.
`CsrfFilter` (mapped on `/*`) implements the Synchronizer Token Pattern.

**Token generation**: 32 bytes from `SecureRandom`, Base64 URL-safe encoded.
Stored in session as `csrfToken`.

**How the token reaches the client**:
- **Thymeleaf forms**: Hidden input `<input type="hidden" name="_csrf" th:value="${csrfToken}" />`
- **SPA (JavaScript)**: Retrieved from login response JSON or meta tag, sent as HTTP header

**Validation on mutating requests** (POST, PUT, DELETE, PATCH):
1. Filter reads `X-CSRF-Token` header (SPA) or `_csrf` form parameter (Thymeleaf)
2. Compares against `session.getAttribute("csrfToken")` using constant-time comparison
3. Mismatch or missing → 403 Forbidden

**Key implementation details** (see `CsrfFilter.java`):
- Session attribute name: `csrfToken`
- HTTP header name: `X-CSRF-Token`
- Form parameter name: `_csrf`
- Comparison: `MessageDigest.isEqual()` (constant-time, prevents timing attacks)

## URL Conventions

- SSR version: `/login`, `/Prodotti`, `/fornitore/home`, `/cliente/home`, etc.
- API version: `/api/login`, `/api/prodotti`, `/api/sku`, `/api/configurazioni`, etc.
- Full API specification: `documentation/API_CONTRACT.md`

## Critical Domain Rules

These constraints must be enforced at the DAO/Servlet level (the DB cannot enforce all of them):

1. **Max depth 4 levels** — check `ProdottoDAO.calcolaLivello()` before INSERT of a child
2. **No cycles** — check `ProdottoDAO.verificaAciclicita()` before adding a child (walk ancestors up)
3. **Unique parent** — a product can have at most one `id_padre` value (inherent to single FK column)
4. **At least one SKU per simple product** — checked before save and before configuration
5. **One SKU per simple product per configuration** — checked in ConfigurazioneServlet before INSERT
6. **Price Snapshotting** — when saving a configuration, COPY current SKU prices into `configurazione_dettaglio.prezzo_unitario_congelato`. Never read prices from `sku` table for saved configurations.

## Error Handling

- **Thymeleaf version**: on validation failure, `forward` (not redirect) back to the same page
  with `request.setAttribute("errori", ...)` and `request.setAttribute("valoriForm", ...)`
- **API version**: return HTTP status codes with JSON body `{"errore": "message", "codice": "ERROR_CODE"}`

## Validation

Always validate **both client-side** (HTML5 attributes or JS) **and server-side**
(Servlet, before calling DAO). Never trust client input.
