# Documentazione TIW — 4 Tabelle

> I controlli di validità (client e server side) e di autorizzazione (server side) sono previsti per tutti gli eventi che li richiedono e non sono riportati per brevità.

---

# MODULO SPA

---

## SPA — Controller / Event Handler

> `makeCall` indica una funzione che fa una chiamata asincrona al server.

### Fornitore

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Controllore** | **Evento** | **Controllore** |
| index → login form → `submit` | `api.fetchJson` | POST username, password | `LoginServlet` (servlet) |
| Home page → `load` | `AppFornitore.init` (makeCall) | GET (nessun parametro) | `HomeFornitoreServlet` (servlet) |
| Form SKU → `submit` | `AppFornitore.handleSubmitSku` (makeCall) | POST dati SKU + foto | `ApiSkuController` (servlet) |
| Form prodotto semplice → `submit` | `AppFornitore.handleSubmitSemplice` (makeCall) | POST JSON (codice, nome, SKU) | `ApiProdottoTreeController` (servlet) |
| Form prodotto composto → `submit` | `AppFornitore.handleCreaComposto` (makeCall) | POST JSON (codice, nome, prezzi, figli) | `ApiProdottoTreeController` (servlet) |
| Tree editor → click **Aggiungi figlio** | `AppFornitore.handleTreeAddChild` | — | — |
| Tree editor → click **Aggiungi SKU** | `AppFornitore.handleTreeAddSku` | — | — |
| Tree editor → click **Scollega** / **Elimina** nodo | `AppFornitore.handleTreeUnlink` / `handleTreeDelete` | — | — |
| Tree editor → `focusout` campo editabile | `AppFornitore.handleTreeInlineEdit` | — | — |
| Tree editor → click **Salva** | `AppFornitore.handleSalvaTree` (makeCall) | POST JSON (azioni pendenti) | `ApiSyncController` (servlet) |
| Ricerca → form `submit` | `AppFornitore.handleSearch` (makeCall) | GET `?q=…` | `ApiRicercaController` (servlet) |
| Risultati → click su item | `AppFornitore.handleExpandSearchResult` (makeCall) | GET `?id=…` | `ApiProdottoController` (servlet) |
| SKU → click **Elimina** | `AppFornitore.handleEliminaSku` (makeCall) | DELETE `?id=…` | `ApiSkuController` (servlet) |
| Logout | redirect `window.location.href` | GET `/logout` | `LoginServlet` (servlet) |

### Cliente

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Controllore** | **Evento** | **Controllore** |
| index → login form → `submit` | `api.fetchJson` | POST username, password | `LoginServlet` (servlet) |
| Home page → `load` | `AppCliente.init` → `caricaCatalogo` (makeCall) | GET (nessun parametro) | `HomeClienteServlet` (servlet) |
| Catalogo → click prodotto | `AppCliente.apriConfigurazione` (makeCall) | GET `?codice=…` | `ApiConfigurazioneController` (servlet) |
| Configura → click **Espandi** nodo | `AppCliente.espandiNodo` | — | — |
| Configura → form `submit` (nuova) | `AppCliente.handleSalvaConfigurazione` (makeCall) | POST JSON (nome, codiceRadice, scelte) | `ApiConfigurazioneController` (servlet) |
| Configura → form `submit` (modifica) | `AppCliente.handleSalvaConfigurazione` (makeCall) | PUT JSON (nome, scelte) | `ApiConfigurazioneController` (servlet) |
| Le mie configurazioni → `load` | `AppCliente.caricaConfigurazioni` (makeCall) | GET `/api/cliente/configurazioni` | `ApiConfigurazioneController` (servlet) |
| Configurazione → click **Dettaglio** | `AppCliente.apriDettaglio` (makeCall) | GET `/api/cliente/configurazioni/{id}` | `ApiConfigurazioneController` (servlet) |
| Configurazione → click **Modifica** | `AppCliente.apriModifica` (makeCall) | GET `/api/cliente/configurazioni/{id}` | `ApiConfigurazioneController` (servlet) |
| Configurazione → click **Clona** | `AppCliente.cloneConfigurazione` (makeCall) | POST JSON (payload clonato) | `ApiConfigurazioneController` (servlet) |
| Configurazione → click **Elimina** | `AppCliente.eliminaConfigurazione` (makeCall) | DELETE `/api/cliente/configurazioni/{id}` | `ApiConfigurazioneController` (servlet) |
| Logout | redirect `window.location.href` | GET `/logout` | `LoginServlet` (servlet) |

---

## SPA — Eventi & Azioni

### Fornitore

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Azione** | **Evento** | **Azione** |
| index → login form → `submit` | Controllo credenziali | POST username, password | Controllo credenziali; creazione sessione |
| Home page → `load` | Aggiorna view con SKU e prodotti orfani disponibili | GET (nessun parametro) | Estrazione SKU e prodotti orfani |
| Form SKU → `submit` | Controllo dati; invio multipart | POST dati SKU + foto | Controllo codice univoco; upload foto; inserimento SKU |
| Form prodotto semplice → `submit` | Controllo dati; invio JSON | POST JSON (codice, nome, SKU) | Controllo codice univoco; inserimento prodotto + associazioni SKU |
| Form prodotto composto → `submit` | Controllo dati e vincoli prezzo; invio JSON | POST JSON (codice, nome, prezzi, figli) | Verifica vincoli prezzo e profondità ≤ 3; inserimento prodotto + figli |
| Tree editor → **Aggiungi figlio / SKU** | Aggiunta nodo/SKU al DOM locale; accodamento azione | — | — |
| Tree editor → **Scollega / Elimina** | Rimozione dal DOM locale; accodamento azione | — | — |
| Tree editor → inline edit `focusout` | Aggiornamento valore nel DOM; accodamento azione UPDATE | — | — |
| Tree editor → click **Salva** | Invio batch azioni pendenti | POST JSON (azioni pendenti) | Esecuzione transazionale delle azioni; aggiornamento DB |
| Ricerca → form `submit` | Aggiorna view con risultati | GET `?q=…` | Ricerca full-text su SKU e prodotti |
| Risultati → click su item | Aggiorna view con albero/dettaglio SKU | GET `?id=…` | Estrazione albero prodotto o dati SKU |
| SKU → click **Elimina** | Rimozione dalla view | DELETE `?id=…` | Eliminazione fisica SKU e configurazioni associate |
| Logout | Cancellazione stato locale; redirect login | GET `/logout` | Invalidazione sessione |

### Cliente

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Azione** | **Evento** | **Azione** |
| index → login form → `submit` | Controllo credenziali | POST username, password | Controllo credenziali; creazione sessione |
| Home page → `load` | Aggiorna view con catalogo prodotti (ordinati Z-A, paginati) | GET (nessun parametro) | Estrazione prodotti composti radice |
| Catalogo → click prodotto | Aggiorna view con albero configurazione | GET `?codice=…` | Estrazione albero prodotto |
| Configura → click **Espandi** nodo | Aggiorna view con i figli del nodo (locale, dati già in RAM) | — | — |
| Configura → form `submit` (nuova) | Controllo dati; aggiorna view con dettaglio | POST JSON (nome, codiceRadice, scelte SKU) | Controllo dati; price snapshotting; inserimento configurazione |
| Configura → form `submit` (modifica) | Controllo dati; aggiorna view con dettaglio | PUT JSON (nome, scelte SKU) | Controllo dati; price snapshotting; aggiornamento configurazione |
| Le mie configurazioni → `load` | Aggiorna view con elenco configurazioni | GET `/api/cliente/configurazioni` | Estrazione configurazioni del cliente |
| Configurazione → click **Dettaglio** | Aggiorna view con dettaglio e prezzi congelati | GET `/api/cliente/configurazioni/{id}` | Estrazione dettaglio + prezzi congelati |
| Configurazione → click **Modifica** | Aggiorna view con form pre-compilato | GET `/api/cliente/configurazioni/{id}` | Estrazione configurazione + albero + scelte |
| Configurazione → click **Clona** | Aggiunge nuova voce all'elenco | POST JSON (payload clonato) | Inserimento copia configurazione |
| Configurazione → click **Elimina** | Rimozione dalla view | DELETE `/api/cliente/configurazioni/{id}` | Eliminazione configurazione |
| Logout | Cancellazione stato locale; redirect login | GET `/logout` | Invalidazione sessione |

---
---

# MODULO SSR

---

## SSR — Controller / Event Handler

### Fornitore

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Controllore** | **Evento** | **Controllore** |
| index → login form → `submit` | `<form action="/login">` | POST username, password | `LoginServlet` (servlet) |
| Home page → `load` | `<a href="/fornitore/home">` | GET (nessun parametro) | `HomeFornitoreServlet` (servlet) |
| Form SKU → `submit` | `<form action="/fornitore/home">` `tipoForm=sku` | POST (dati SKU + foto) | `HomeFornitoreServlet.handleCreaSku` (servlet) |
| Form prodotto semplice → `submit` | `<form action="/fornitore/home">` `tipoForm=semplice` | POST (codice, nome, IDs SKU) | `HomeFornitoreServlet.handleCreaSemplice` (servlet) |
| Form prodotto composto → **Ricalcola** | `<form action="/fornitore/home">` `azione=ricalcola` | POST `azione=ricalcola` | `HomeFornitoreServlet.handleRicalcolaComposto` (servlet) |
| Form prodotto composto → `submit` | `<form action="/fornitore/home">` `tipoForm=composto` | POST (dati composto + IDs figli) | `HomeFornitoreServlet.handleCreaComposto` (servlet) |
| Risultato → click **Elimina** | `<form action="/fornitore/azione">` `azione=ELIMINA` | POST (tipo, ID oggetto) | `AzioneCatalogoServlet` (servlet) |
| Risultato → click **Rimuovi** | `<form action="/fornitore/azione">` `azione=RIMUOVI` | POST (tipo, ID oggetto, ID padre) | `AzioneCatalogoServlet` (servlet) |
| Ricerca → form `submit` | `<form action="/fornitore/cerca">` | GET `?keyword=…` | `CercaCatalogoServlet` (servlet) |
| Risultato ricerca → `load` | redirect interno | GET (nessun parametro) | `RisultatoFornitoreServlet` (servlet) |
| Logout | `<form action="/logout">` | POST | `LogoutServlet` (servlet) |

### Cliente

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Controllore** | **Evento** | **Controllore** |
| index → login form → `submit` | `<form action="/login">` | POST username, password | `LoginServlet` (servlet) |
| Home page (catalogo) → `load` | `<a href="/cliente/home">` | GET (nessun parametro) | `HomeClienteServlet` (servlet) |
| Catalogo → click prodotto | `<a href="/cliente/configura?codice=…">` | GET `?codice=…` | `ConfiguraServlet` (servlet) |
| Configura → click **Espandi** nodo | `<form action="/cliente/configura">` `espandi=<id>` | POST `espandi=<id>` | `ConfiguraServlet.doPost` → redirect GET (servlet) |
| Configura → form `submit` | `<form action="/cliente/configura">` | POST (nome, codiceRadice, `sku_*`) | `ConfiguraServlet.doPost` → forward a `SalvaConfigurazioneServlet` (servlet) |
| Le mie configurazioni → `load` | `<a href="/cliente/configurazioni">` | GET (nessun parametro) | `MieConfigurazioniServlet` (servlet) |
| Dettaglio configurazione → `load` | `<a href="/cliente/dettaglio?idConfig=…">` | GET `?idConfig=…` | `DettaglioConfigurazioneServlet` (servlet) |
| Logout | `<form action="/logout">` | POST | `LogoutServlet` (servlet) |

---

## SSR — Eventi & Azioni

### Fornitore

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Azione** | **Evento** | **Azione** |
| index → login form → `submit` | Controllo credenziali | POST username, password | Controllo credenziali; creazione sessione; redirect home |
| Home page → `load` | Aggiorna view con SKU e prodotti orfani | GET (nessun parametro) | Estrazione SKU e prodotti orfani |
| Form SKU → `submit` | Validazione dati form | POST (dati SKU + foto) | Controllo codice univoco; upload foto; inserimento SKU |
| Form prodotto semplice → `submit` | Validazione dati form | POST (codice, nome, IDs SKU) | Calcolo prezzoMin/Max da SKU; inserimento prodotto + associazioni |
| Form prodotto composto → **Ricalcola** | Nessuna azione (semplice submit) | POST `azione=ricalcola` | Calcolo somma prezzoMin/Max figli; salvataggio in sessione |
| Form prodotto composto → `submit` | Validazione dati form | POST (dati composto + IDs figli) | Verifica vincoli prezzo e profondità ≤ 3; inserimento transazionale |
| Risultato → click **Elimina** | Nessuna azione client | POST `azione=ELIMINA` (tipo, ID oggetto) | Rimozione configurazioni clienti collegate; eliminazione fisica |
| Risultato → click **Rimuovi** | Nessuna azione client | POST `azione=RIMUOVI` (tipo, ID, padre) | Rimozione associazione SKU/figlio; ricalcolo prezzi padre |
| Ricerca → form `submit` | Nessuna azione client | GET `?keyword=…` | Ricerca testuale su SKU e prodotti |
| Logout | Nessuna azione client | POST | Invalidazione sessione; redirect login |

### Cliente

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Azione** | **Evento** | **Azione** |
| index → login form → `submit` | Controllo credenziali | POST username, password | Controllo credenziali; creazione sessione; redirect home |
| Home page (catalogo) → `load` | Aggiorna view con prodotti radice | GET (nessun parametro) | Estrazione prodotti composti radice |
| Catalogo → click prodotto | Nessuna azione client (link) | GET `?codice=…` | Estrazione albero prodotto da DB; rendering form configurazione |
| Configura → click **Espandi** nodo | Invio scelte SKU correnti come hidden field | POST `espandi=<id>` + `sku_*` + `aperto=*` | Redirect GET con query string aggiornata (PRG stateless) |
| Configura → form `submit` | Validazione nome non vuoto; completezza scelte SKU | POST (nome, codiceRadice, `sku_*`) | Controllo completezza; price snapshotting; inserimento/aggiornamento configurazione |
| Le mie configurazioni → `load` | Aggiorna view con elenco configurazioni | GET (nessun parametro) | Estrazione configurazioni del cliente (ordinate per data) |
| Dettaglio configurazione → `load` | Aggiorna view con dettaglio e prezzi congelati | GET `?idConfig=…` | Estrazione dettaglio configurazione + prezzi congelati |
| Configurazione → click **Elimina** | Nessuna azione client | POST | Eliminazione configurazione |
| Logout | Nessuna azione client | POST | Invalidazione sessione; redirect login |

---
---

# PARTI COMUNI CLIENTE & FORNITORE

---

## SPA — Parti comuni (Controller / Event Handler)

> Questi eventi e controllori sono **identici** per entrambi i ruoli.

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Controllore** | **Evento** | **Controllore** |
| index → login form → `submit` | `api.fetchJson` (POST) | POST username, password | `LoginServlet` (servlet) |
| Home page → `load` (recupero info utente) | `AppFornitore.init` / `AppCliente.init` → `api.getUser` (makeCall) | GET `/api/me` | `ApiUserController` (servlet) |
| Home page → `load` (recupero CSRF token) | lettura meta tag `_csrf` dal DOM | GET `/api/me` | `ApiUserController` (servlet) |
| Sessione scaduta (401 su qualsiasi fetch) | intercetto in `api.fetchJson` → redirect automatico | — | — |
| Logout | redirect `window.location.href = 'login.html'` | GET `/logout` | `LoginServlet` (servlet) |

## SPA — Parti comuni (Eventi & Azioni)

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Azione** | **Evento** | **Azione** |
| index → login form → `submit` | Controllo credenziali | POST username, password | Controllo credenziali; creazione sessione; generazione CSRF token |
| Home page → `load` (recupero info utente) | Visualizza nome utente nella navbar; imposta CSRF token nel meta tag | GET `/api/me` | Lettura sessione; restituzione username, nome, cognome, ruolo, CSRF token |
| Sessione scaduta (401 su qualsiasi chiamata) | Redirect automatico a `login.html` | — | — |
| Logout | Cancellazione stato locale; redirect a `login.html` | GET `/logout` | Invalidazione sessione HTTP |

---

## SSR — Parti comuni (Controller / Event Handler)

> Questi eventi e controllori sono **identici** per entrambi i ruoli.

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Controllore** | **Evento** | **Controllore** |
| index → login form → `submit` | `<form action="/login" method="POST">` | POST username, password | `LoginServlet` (servlet) |
| Sessione assente / scaduta (su qualsiasi pagina) | redirect automatico (nessun controller client) | — (rilevato dai filtri di sessione nelle servlet) | redirect a `/login` |
| Logout | `<form action="/logout" method="POST">` | POST | `LogoutServlet` (servlet) |

## SSR — Parti comuni (Eventi & Azioni)

| **Client side** | | **Server side** | |
|---|---|---|---|
| **Evento** | **Azione** | **Evento** | **Azione** |
| index → login form → `submit` | Controllo credenziali | POST username, password | Controllo credenziali; creazione sessione; generazione CSRF token; redirect home per ruolo |
| Sessione assente / scaduta (su qualsiasi pagina) | Nessuna azione client | — | Redirect a `/login` |
| Logout | Nessuna azione client | POST | Invalidazione sessione HTTP; redirect a `/login` |
