# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a university project for "Tecnologie Informatiche per il Web" (TIW) at Politecnico di Milano. It implements a **Product Configurator** web app in **two separate versions**:

- `TIW-HTML/` — Pure HTML version (multi-page, traditional form submits)
- `progetto TIW/TIW_project/` — JavaScript SPA version (single-page, async fetch)

Both are deployed as separate WAR files on **Apache Tomcat 10.1** (Jakarta EE 9+, use `jakarta.*` namespace, not `javax.*`).

## Build & Deploy

There is no Maven or Gradle setup — projects are built via Eclipse as Dynamic Web Projects and deployed manually to Tomcat. To compile and deploy:

1. Right-click project in Eclipse → Export → WAR file
2. Drop WAR into Tomcat's `webapps/` folder
3. Start Tomcat: `$CATALINA_HOME/bin/startup.sh`

The `Servers/` directory contains Tomcat's `context.xml`. DB connection parameters (`dbDriver`, `dbUrl`, `dbUser`, `dbPassword`) are configured as `init-param` in each app's `WEB-INF/web.xml`.

## Architecture

Both versions share the same MVC pattern with Servlet controllers. The package structure under `it.polimi.tiw` is:

```
model/      — POJOs: Utente, Prodotto, SKU, Configurazione
dao/        — DB access only: UtenteDAO, ProdottoDAO, SKUDAO, ConfigurazioneDAO
servlet/
  html/     — Servlet for HTML version (forward to Thymeleaf/JSP views)
  js/       — Servlet for JS version (return JSON responses)
filter/     — AuthenticationFilter + RoleFilter (mapped on /fornitore/*, /cliente/*, /api/*)
utils/      — ConnectionFactory (DriverManager, params from web.xml). Password hashing is done in UtenteDAO via BCrypt (jBCrypt, `BCrypt.checkpw`), not a separate PasswordUtils/SHA-256 class
```

**Key constraint**: DAOs contain only SQL. Servlets contain only orchestration. Views contain only presentation.

## Database

MySQL with the schema defined in `documentation/CONTRATTO_ARCHITETTURALE.md` (section 2). Key tables: `utente`, `prodotto` (single-table hierarchy with `tipo ENUM('SEMPLICE','COMPOSTO')` and `parent_codice` adjacency list), `sku`, `prodotto_sku`, `configurazione`, `configurazione_sku`.

Use `PreparedStatement` always — never raw `Statement`. Connections are obtained via `ConnectionFactory.getConnection(servletContext)`, which uses `DriverManager` with parameters read from `web.xml` init-params (there is no JNDI DataSource / connection pool). Two connection-management patterns coexist in the codebase: most servlets open one connection as a field in `init()` and close it in `destroy()`; a couple of SSR servlets (`CercaCatalogoServlet`, `AzioneCatalogoServlet`) open a per-request connection via try-with-resources in `doGet`/`doPost`.

## Critical Domain Rules

These constraints must be enforced at the DAO/Servlet level (the DB cannot enforce all of them):

1. **Max depth 4 levels** — check `ProdottoDAO.calcolaLivello()` before INSERT of a child
2. **No cycles** — check `ProdottoDAO.verificaAciclicita()` before adding a child (walk ancestors up)
3. **Unique parent** — a product can have at most one parent (guaranteed by schema `UNIQUE` on `parent_codice`)
4. **At least one SKU per simple product** — checked before save and before configuration
5. **One SKU per simple product per configuration** — checked in `ConfigurazioneServlet` before INSERT

## URL Conventions

- HTML version: `/login`, `/fornitore/home`, `/fornitore/ricerca`, `/cliente/home`, `/cliente/scelta-sku`, `/cliente/dettaglio`, `/cliente/configurazioni`
- JS version: `/api/sku`, `/api/prodotti`, `/api/prodotti/{codice}/figli`, `/api/configurazioni`, etc.
- Session key for the logged-in user object: `"utente"` (use `SessionConstants` class)

## Authentication

`AuthenticationFilter` intercepts all requests. It:
1. Lets `/login` and `/static/*` pass through
2. Redirects unauthenticated users to `/login`
3. Returns 403 if the user's role doesn't match the path (`/fornitore/*` vs `/cliente/*`)

Servlets also independently verify the role — do not rely solely on the filter.

## Error Handling

- **HTML version**: on validation failure, `forward` (not redirect) back to the same page with `request.setAttribute("errori", ...)` and `request.setAttribute("valoriForm", ...)` to repopulate fields
- **JS version**: return HTTP status codes (`400`, `401`, `403`, `404`, `500`) with JSON body `{"errore": "message"}`

## Validation

Always validate **both client-side** (HTML5 attributes or JS) **and server-side** (Servlet, before calling DAO). Never trust client input.
