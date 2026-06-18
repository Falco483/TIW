# Relazione Dettagliata del Modulo `tiw-spa`

Il modulo **`tiw-spa`** implementa l'interfaccia utente come **Single Page Application (SPA)** pura. Interagisce con il backend quasi esclusivamente tramite chiamate asincrone (`fetch` in JavaScript) scambiando dati in formato JSON, e il rendering del DOM avviene interamente lato client (browser).

Questo modulo dipende da `tiw-core` per tutta la logica di accesso ai dati e i filtri di sicurezza. Di seguito un'analisi dei file e della loro funzione.

---

## 1. Configurazione del Progetto

### `pom.xml`
- Ha un `packaging` di tipo `war` (Web Application Archive).
- Include `tiw-core` tra le dipendenze, sfruttando così i DAO, DTO e Filtri condivisi.
- Include **Jackson (`jackson-databind` e `jackson-datatype-jsr310`)**: questa è un'aggiunta esclusiva della SPA, fondamentale per serializzare e deserializzare i POJO (e le LocalDateTime) in formato JSON nelle API REST.

### `src/main/webapp/WEB-INF/web.xml`
- Oltre ai parametri di connessione al DB, mappa esplicitamente i tre filtri di sicurezza prelevati da `tiw-core` (`CsrfFilter`, `AccessControlFilter`, `RoleFilter`) mappandoli su `/*`.
- Configura l'applicazione per intercettare ogni richiesta HTTP, proteggendo gli endpoint API.

---

## 2. API Servlet Controllers (Package `it.polimi.tiw.servlet.api`)

A differenza delle classiche Servlet, queste classi si comportano come controller RESTful. Leggono JSON dall'`InputStream` della richiesta e scrivono JSON nell'`OutputStream` della risposta, usando l'`ObjectMapper` di Jackson.

- **`ApiConfigurazioneController.java`**:
  Gestisce le configurazioni del Cliente. Espone metodi GET per la lista e i dettagli, POST per il salvataggio di una nuova configurazione e PUT/DELETE per la modifica e cancellazione. Restituisce le risposte mappate in JSON (incluso il controllo di validità dell'albero e dei prezzi).
- **`ApiProdottoController.java`**:
  Endpoint CRUD base per il Fornitore riguardo ai Prodotti.
- **`ApiProdottoTreeController.java`**:
  Fornisce l'albero completo dei prodotti, sfruttando il Composite Pattern implementato nel core, serializzandolo ricorsivamente in JSON.
- **`ApiRicercaController.java`**:
  Risponde alle query della "Search Bar", incrociando i dati di `ProdottoDAO` e `SKUDAO` restituendo un array di `ElementoCatalogo` uniformato.
- **`ApiSkuController.java`**:
  Fornisce l'accesso CRUD per le varianti SKU. Può gestire richieste Multipart se è presente un upload di immagini.
- **`ApiSyncController.java`**:
  Un endpoint avanzato che accetta un array di "Azioni" (es. aggiunte, rimozioni, spostamenti di nodi) e le esegue transazionalmente in batch sul database. Questo ottimizza drasticamente le prestazioni del "Tree Editor" interattivo lato client, inviando una singola richiesta invece di dozzine.
- **`ApiUserController.java`**:
  Restituisce il profilo dell'utente correntemente loggato (`/api/me`), usato dal frontend per stampare "Benvenuto {Nome}".

---

## 3. Web Entry-point (Package `it.polimi.tiw.servlet.web`)

Queste servlet fungono unicamente da entry-point o dispatcher per servire i file HTML statici della SPA.
- **`LoginServlet.java`**: Gestisce il submit del form di login tradizionale, crea la sessione e redirige l'utente verso la sua home (`/cliente/` o `/fornitore/`).
- **`HomeClienteServlet.java` & `HomeFornitoreServlet.java`**: Redirigono o servono i rispettivi file `home-cliente.html` e `home-fornitore.html`. Da qui in poi, il controllo passa integralmente a JavaScript.

---

## 4. Frontend Javascript (`src/main/webapp/js/`)

L'intelligenza della UI risiede nei file Javascript. Non si fa uso di framework (come React o Angular), ma si sfrutta Vanilla JS con l'ausilio di chiamate asincrone.

### `api.js` (Network Layer)
- Centralizza tutte le chiamate `fetch` verso gli endpoint API.
- **Gestione CSRF**: Implementa una funzione automatica (`fetchJson`) che legge il meta tag `<meta name="_csrf">` iniettato dalla Servlet e lo allega nell'header `X-CSRF-Token` per tutte le richieste mutanti (POST, PUT, DELETE).
- Centralizza la gestione degli errori e il redirect automatico al login in caso di ricezione dello status HTTP `401 Unauthorized`.

### `app-fornitore.js`
- Pilota l'interfaccia complessa del Fornitore.
- Gestisce la costruzione dinamica del DOM: la griglia del catalogo, l'editor visuale dell'albero dei prodotti, il drag-and-drop (o logiche affini) per la composizione.
- Raccoglie i form (es. creazione Prodotto Composto) intercettando il `submit`, disabilitando il comportamento di default, e chiamando `api.js` per inviare JSON.
- Implementa logiche di validazione lato client (es. controllo formato codice, presenza di sotto-prodotti).

### `app-cliente.js`
- Pilota la dashboard del Cliente.
- Recupera via API le configurazioni salvate e manipola il DOM per costruire lo storico ordini.
- Gestisce la complessa vista di "Configurazione Nuovo Prodotto", mostrando step-by-step o rami gerarchici e permettendo all'utente di selezionare le SKU associate a ciascun nodo foglia.

---

## 5. Risorse Statiche
- **`home-cliente.html`**, **`home-fornitore.html`**, **`login.html`**: Forniscono l'impalcatura base vuota (scaffolding). Il loro body è costituito prevalentemente da `div` vuoti che verranno poi "popolati" dai file Javascript. Includono il `<meta name="_csrf" content="...">` per il token di sicurezza.
- **`style.css`**: Il file di stile Vanilla CSS che conferisce al software l'estetica.

---

> [!TIP]
> **Architettura SPA**: Separando nettamente le API (`Api...Controller`) dalla presentazione (`app-....js`), questo modulo scarica enormemente il server rispetto all'approccio SSR, poiché il server invia solo dati grezzi in JSON e il client si occupa del rendering. La sicurezza CSRF è stata elegantemente risolta comunicando i token tramite Custom Headers.
