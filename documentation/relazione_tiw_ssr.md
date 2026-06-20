# Relazione Dettagliata del Modulo `tiw-ssr`

Il modulo **`tiw-ssr`** implementa l'applicazione tramite **Server-Side Rendering (SSR)**. Tutta la logica di presentazione e manipolazione del DOM avviene lato server. Il server elabora la richiesta, preleva i dati dai DAO, "fonde" i dati con dei template HTML (usando Thymeleaf) e restituisce un'unica pagina HTML completamente formata al browser dell'utente.

Anche questo modulo dipende fortemente da `tiw-core` per accesso ai dati (DAO) e sicurezza (Filter).

---

## 1. Configurazione del Progetto

### `pom.xml`
- Ha un `packaging` di tipo `war` (Web Application Archive).
- Include `tiw-core` come dipendenza principale.
- Include **`thymeleaf`**: Questa è la dipendenza caratterizzante del modulo. Thymeleaf è il template engine Java responsabile dell'elaborazione delle view lato server. Non c'è traccia di Jackson poiché qui non si elaborano API JSON.

### `src/main/webapp/WEB-INF/web.xml`
- Dichiara i parametri del database.
- Registra e mappa su `/*` i filtri condivisi (`CsrfFilter`, `AccessControlFilter`, `RoleFilter`).
- *Nota*: in ambito SSR, la gestione del token CSRF (implementata nel core) è agevolata da Thymeleaf, che spesso permette di iniettare il parametro `_csrf` direttamente in tutti i tag `<form>` nascosti.

---

## 2. Controller Servlet (Package `it.polimi.tiw.servlet.web`)

Il modulo è caratterizzato da molte Servlet granulari (Pattern Page Controller). Ogni servlet gestisce uno specifico URL, processa il form/i parametri HTTP, chiama il Model/DAO e delega la renderizzazione finale a un template Thymeleaf.

### Area Comune e Sicurezza
- **`LoginServlet.java`**: Processa le credenziali utente (POST) usando `UtenteDAO`. In caso di successo, crea la sessione e fa un redirect verso `HomeClienteServlet` o `HomeFornitoreServlet` in base al ruolo.
- **`LogoutServlet.java`**: Invalida la sessione (`session.invalidate()`) e redirige l'utente al login.

### Area Fornitore
Le servlet del fornitore richiamano tipicamente `ProdottoDAO` e `SKUDAO` per le operazioni CRUD. L'interfaccia utente impone numerosi reload di pagina (non essendo una SPA).
- **`HomeFornitoreServlet.java`**: Recupera e impagina l'elenco dei prodotti radice per mostrarli sulla pagina del catalogo. Richiama il motore Thymeleaf puntando a `fornitore/home.html`.
- **`AzioneCatalogoServlet.java`**, **`RimuoviServlet.java`**: Ricevono richieste POST dal browser (spesso da link o pulsanti di form) per modificare l'albero dei prodotti o per eliminare definitivamente componenti, eseguendo il task via DAO e poi redirigendo alla Home.
- **`CercaCatalogoServlet.java`** / **`RisultatoFornitoreServlet.java`**: Servlets preposte a leggere la `query` string di ricerca dell'utente e restituire la vista con i risultati aggregati (`fornitore/risultato.html`).

### Area Cliente
- **`HomeClienteServlet.java`**: Carica lo stato iniziale del cliente e inoltra alla view `cliente/home.html`.
- **`MieConfigurazioniServlet.java`** e **`DettaglioConfigurazioneServlet.java`**: Recuperano dal DAO lo storico delle "build" del cliente e presentano il dettaglio.
- **`ConfiguraServlet.java`** e **`SceltaSkuServlet.java`**: Gestiscono il wizard step-by-step di composizione. Siccome la pagina viene ricaricata ad ogni selezione, la selezione corrente deve essere immagazzinata in sessione (o in parametri URL) tra uno step e l'altro prima di approdare al checkout.
- **`SalvaConfigurazioneServlet.java`** e **`ConfigurazioneActionServlet.java`**: L'endpoint finale POST che preleva tutte le scelte aggregate fatte dall'utente, genera un oggetto `Configurazione` e ne richiede l'inserimento sul database tramite `ConfigurazioneDAO`.

### Varie
- **`WebProdottoController.java`**: Un controller di utilità misto che fa da collante per le visualizzazioni gerarchiche complesse che richiedono il caricamento di un intero ramo (`ProdottoDAO.getAlberoProdotto()`).
- **`RicercaServlet.java`** / **`DettaglioServlet.java`**: Per consentire a cliente o fornitori di guardare nel dettaglio una specifica entità recuperata.

---

## 3. Template Engine (Thymeleaf in `WEB-INF/templates/`)

Invece di JavaScript e HTML statici, la UI è generata fondendo l'HTML con direttive specifiche di Thymeleaf (`th:*`).

### I Template
- **`login.html`**: Form di accesso standard (con supporto visuale in caso di "credenziali errate" passate via model attributi).
- **`fornitore/home.html`**, **`cliente/home.html`**: Dashboard principali. Utilizzano i cicli nativi di Thymeleaf (`th:each="prodotto : ${prodotti}"`) per iterare sulle liste provenienti dalle Servlet e costruire le tabelle HTML in tempo reale sul server.
- **`fragments/navbar.html`**: Frammento riusabile incluso (`th:replace` o `th:insert`) nelle altre pagine per garantire coerenza visiva su tutte le schermate senza duplicare il codice HTML.
- **`configura.html`** / **`cliente/sceltaSku.html`**: Presentano i form di immissione dati.
  
> [!IMPORTANT]
> **Gestione Form e CSRF**: In Thymeleaf, ogni POST è implicitamente protetto se il template utilizza l'attributo nativo o se lo sviluppatore inserisce consapevolmente un input hidden (`<input type="hidden" name="_csrf" th:value="${csrfToken}"/>`). Il controller Java intercetterà la richiesta e `CsrfFilter` la lascerà passare solo se i token corrispondono.

---

> [!TIP]
> **Architettura SSR**: Questo approccio multipagina (MPA) ricarica integralmente la risorsa a ogni click. Semplifica lo sviluppo perché tutto il tracciamento dei permessi e i dati sono accessibili istantaneamente sulla memoria JVM (session, request object), eliminando la complessità legata alla sincronizzazione dei client (come nel `ApiSyncController` della SPA). Tuttavia, appesantisce il carico di banda e computazione del server, offrendo un'esperienza utente tipicamente meno fluida rispetto alla SPA.
