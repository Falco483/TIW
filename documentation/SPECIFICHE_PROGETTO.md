# Configuratore di Prodotto - Specifiche Progetto

## 📋 Indice
1. [Regole di Svolgimento](#regole-di-svolgimento)
2. [Descrizione del Progetto](#descrizione-del-progetto)
3. [Entità, Fasi e Ruoli](#entità-fasi-e-ruoli)
4. [Modalità di Accesso](#modalità-di-accesso)
5. [Vincoli Espliciti](#vincoli-espliciti)
6. [Interfacce](#interfacce)
7. [Aspetti Comuni](#aspetti-comuni)

---

## Regole di Svolgimento

### Presentazione e Valutazione
- **Invio documentazione**: almeno 3 giorni prima della presentazione
- **Formato documentazione**: secondo gli esempi mostrati a lezione e disponibili su WeBeep
- **Presentazione**: tramite MS Teams, individuale per ogni membro del gruppo
- **Durata**: colloquio contemporaneo per tutti i membri
- **Presenza**: obbligatoria 5 minuti prima dell'orario concordato

### Requisiti di Dimostrazione
- **Database**: preparare dati sufficienti per testare TUTTI gli scenari di uso
- **Ambiente**: utilizzare zoom dell'IDE e browser debugger; **tema chiaro** (sfondo bianco)
- **Preparazione**: testare MS Teams (audio, screen sharing) con un collega
- **Credenziali**: tenere a portata di mano l'elenco degli utenti per il test

### Commenti Generali (Qualità del Codice)
- **Versioni**: HTML puro e JavaScript devono essere **applicazioni web distinte**
- **Validazione**: sempre **sia lato client che lato server**
- **Gestione errori**: in caso di errore nel salvataggio form, ricaricare la pagina **preservando i valori** inseriti e segnalare i campi errati
- **Controllo permessi**: un utente non può fare operazioni al di fuori del suo ruolo
- **Sicurezza**: impedire violazioni della sicurezza tramite invio di parametri scorretti
- **Funzioni opzionali**: modifica/cancellazione dei dati sono opzionali e non valutate

---

## Descrizione del Progetto

Un **Configuratore di Prodotto** è un'applicazione web che consente ai **fornitori** di creare prodotti configurabili e ai **clienti** di configurarli in base alle loro esigenze.

### Esempio Pratico: PC Configurabile
Un PC configurabile è una struttura gerarchica:
```
PC Desktop (livello 1)
├── Case (livello 2 - semplice)
├── Sistema di Elaborazione (livello 2 - composto)
│   ├── Scheda Madre (livello 3 - semplice)
│   ├── CPU (livello 3 - semplice)
│   ├── RAM (livello 3 - semplice)
│   └── GPU (livello 3 - semplice)
├── Sistema di Memoria (livello 2 - composto)
│   ├── Disco Fisso 1 (livello 3 - semplice)
│   ├── Disco Fisso 2 (livello 3 - semplice)
│   └── RAID (livello 3 - semplice)
├── Alimentazione (livello 2 - semplice)
└── Raffreddamento (livello 2 - semplice)
```

---

## Entità, Fasi e Ruoli

### 1. Prodotto Composto
- **Attributi**: codice, nome, descrizione, fascia di prezzo (min/max)
- **Composizione**: contiene uno o più sottoprodotti (semplici o composti)
- **Ruolo**: creato dal fornitore, configurato dal cliente

### 2. Prodotto Semplice
- **Attributi**: codice, nome
- **Associazione**: collegato a una o più SKU
- **Esempio**: CPU, RAM, Case, ecc.

### 3. SKU (Stock Keeping Unit)
- **Attributi**: codice (intero), nome, fotografia, descrizione tecnica, prezzo
- **Scopo**: realizzazione concreta di un prodotto semplice
- **Esempio**: CPU - AMD Ryzen 5 9500F, CPU - AMD Ryzen 7 7700X, ecc.

### 4. Configurazione di Prodotto
- **Creatore**: cliente
- **Composizione**: selezione di una SKU per ogni prodotto semplice
- **Metadati**: nome, data creazione, prezzo totale (somma dei prezzi SKU)

### 5. Utente
- **Tipi**: fornitore o cliente (nessun utente può avere entrambi i ruoli)
- **Autenticazione**: username e password
- **Database**: anagrafica conservata nel DB

---

## Modalità di Accesso

L'applicazione prevede l'accesso tramite **login con credenziali** (username + password).

Ogni ruolo ha un'interfaccia diversa con funzionalità specifiche.

---

## Vincoli Espliciti

1. **Ogni prodotto semplice deve avere almeno una SKU**
2. **Ogni configurazione deve selezionare una SKU per ogni prodotto semplice**
3. **Prezzi ≥ 0**
4. **Gerarchia aciclica**: non è possibile inserire come sottoprodotto un prodotto che contiene già il prodotto corrente (direttamente o indirettamente)
5. **Profondità massima**: 3 livelli sotto il prodotto di primo livello (4 livelli totali)
6. **Unicità del padre**: un prodotto semplice o composto può appartenere a un solo prodotto padre
7. **Polimorfismo SKU**: una SKU può essere associata a uno o più prodotti semplici

---

## Interfacce

### 🔧 INTERFACCIA FORNITORE

#### Pagina HOME FORNITORE
Contiene 3 form per creare:

**1. SKU**
- Campi: codice, nome, fotografia, descrizione tecnica, prezzo
- Bottone: SALVA
- Risultato: nuova SKU salvata nel DB

**2. Prodotto Semplice**
- Campi: codice, nome
- Selezione: checkboxes per associare SKU (ordinate per codice decrescente)
- Bottone: SALVA
- Risultato: prodotto semplice + associazioni SKU salvate nel DB

**3. Prodotto Composto**
- Campi: codice, nome, descrizione, fascia di prezzo (min-max)
- Selezione: checkboxes per associare sottoprodotti semplici/composti (ordinate per nome decrescente)
- Vincolo: profondità massima 3 livelli sotto il primo livello
- Bottone: SALVA
- Risultato: prodotto composto + associazioni sottoprodotti salvate nel DB

#### Visualizzazione dopo Salvataggio

**Se SKU creata:**
- Visualizzare solo gli attributi della SKU

**Se Prodotto Semplice creato:**
- Attributi del prodotto + lista delle SKU associate

**Se Prodotto Composto creato:**
- Attributi del prodotto + lista nidificata multilivello ricorsiva di tutti i componenti e SKU associate

#### Pagina RICERCA PRODOTTI

**Form di ricerca:**
- Un campo di input
- Bottone RICERCA
- Ricerca **case-insensitive** su:
  - Nome e descrizione di prodotti
  - Nome e descrizione tecnica di SKU

**Risultati:**
- Elenco cliccabile
- Click su elemento: visualizzazione completa attributi + associazioni

**Link disponibili:**
- **RIMUOVI** (accanto a SKU di prodotto semplice): elimina la relazione SKU-prodotto
- **RIMUOVI** (accanto a sottoprodotto): elimina la relazione sottoprodotto-prodotto padre
- **ELIMINA** (su ogni oggetto): elimina l'oggetto e le sue relazioni ricorsivamente

**Comportamento post-azione:**
- Aggiornamento DB
- Ricarico HOME FORNITORE
- Aggiornamento visualizzazione dati

#### Modifica di Prodotto
- Avviene mediante: **cancellazione + creazione** con stesso codice e nome
- Riassegnazione di sottoprodotti o SKU

---

### 👤 INTERFACCIA CLIENTE

#### Pagina HOME CLIENTE
- **Contenuto**: elenco di tutti i prodotti composti di primo livello
- **Ordinamento**: alfabetico per nome **decrescente**
- **Paginazione**: se > 10 prodotti, mostrare inizialmente 10
  - Bottoni PRECEDENTI/SUCCESSIVI per navigare
  - Bottoni visibili solo se esiste rispettivo insieme

#### Pagina SCELTA SKU
- **Visualizzazione**: schema gerarchico del prodotto selezionato
- **Interazione**: 
  - Clic su prodotto di livello 2 → mostra prodotti di livello 3
  - Clic su prodotto di livello 3 → mostra prodotti di livello 4 (se esistono)
- **Selezione SKU**: menu a tendina accanto a ogni prodotto semplice
- **Salvataggio**: bottone SALVA
  - Richiede: nome per la configurazione
  - Salva: configurazione associata a cliente + data + prezzo totale (somma SKU)

**Esempio di visualizzazione:**
```
PC Desktop
├── Case >|__Case-sku1| __|__Case-sku2| __| …
├── Sistema di elaborazione
│   ├── CPU >|__CPU-sku1| __|__CPU-sku2| __| …
│   ├── GPU >|__GPU-sku1| __|__GPU-sku2| __| …
│   └── RAM >|__RAM-sku1| __|__RAM-sku2| __| …
[SALVA]
```

**Validazione salvataggio:**
- Se SKU non selezionata: errore + riproposta pagina SCELTA SKU con selezioni precedenti
- Se OK: pagina DETTAGLIO CONFIGURAZIONE

#### Pagina DETTAGLIO CONFIGURAZIONE
- Mostra: SKU selezionate con dati + prezzo totale

#### Pagina LE MIE CONFIGURAZIONI
- **Contenuto**: tutte le configurazioni dell'utente in ordine data decrescente
- **Azioni disponibili**:
  - Bottone CANCELLA: elimina configurazione
  - Bottone CLONA: duplica configurazione
  - Link MODIFICA: apre SCELTA SKU per modificare selezioni SKU
    - Al salvataggio: aggiorna data ultima modifica

---

## Versioni HTML e JavaScript

### Versione HTML Pura
- Pagine distinte per ogni schermata
- Form tradizionali con submit
- Ricaricamenti pagina

### Versione JavaScript
Le specifiche della versione JavaScript **estendono** quella HTML:

#### Interfaccia Fornitore - Pagina Unica
- Una sola pagina (Single Page Application)
- Richieste asincrone al server
- Aggiornamento parziale dell'interfaccia

**SKU:**
- Form per creazione
- Bottone SALVA SKU → mostra attributi della SKU
- Click su attributo → campo input editabile
- Salvataggio automatico su blur (uscita mouse)

**Prodotto Composto:**
- Creazione tramite form con attributi
- Bottone SALVA PRODOTTO → creazione a lato client
- Click su attributo → editabile con input
- Nuovo valore → salva con bottone SALVA PRODOTTO

**Bottoni di Gestione Gerarchia:**

| Bottone | Dove | Azione |
|---------|------|--------|
| **+** | Accanto prodotto composto | Menu: "sottoprodotto composto" oppure "sottoprodotto semplice" |
| | | Crea sottoprodotto nidificato sotto il prodotto |
| **+** | Accanto prodotto semplice | Menu: "nuova SKU" oppure "SKU esistente" |
| | | "Nuova SKU" → form creazione |
| | | "SKU esistente" → menu a scelta multipla SKU |
| **-** | Accanto sottoprodotto (se ha padre) | Cancella relazione sottoprodotto-padre (sottoprodotto rimane in DB) |
| **-\*** | Accanto prodotto | Cancella il prodotto + sottoprodotti ricorsivi |
| | | SKU: eliminate solo se non associate ad altri prodotti |
| **-** | Accanto SKU | Cancella relazione SKU-prodotto |
| **-\*** | Accanto SKU | Elimina SKU dal DB |

- Salvataggio: bottone SALVA PRODOTTO salva tutte le modifiche nel DB
- Errore: mancanza SKU in prodotto semplice → messaggio errore ma **dati client preservati**

**Ricerca Prodotti:**
- Integrata nella stessa pagina
- Modifica/eliminazione con stesse modalità della creazione

#### Interfaccia Cliente - Pagina Unica
- Una sola pagina (Single Page Application)
- Richieste asincrone al server
- Aggiornamento parziale

---

## Aspetti Comuni

### Tutte le Interfacce
- **Link LOGOUT**: annulla sessione, ritorna a login
- **Messaggi di saluto**: HOME page mostra nome e cognome dell'utente
- **Link HOME**: nelle pagine non-home, riporta alla home page

### Interfacce Multi-Pagina (HTML)
- Ogni pagina non-home contiene link HOME

---

## 🎯 Funzionalità Opzionali (NON Richieste per la Sufficienza)

### Undo/Redo Lato Client (Versione JavaScript)
Operazioni annullabili:
- Creazione prodotto composto
- Inserimento attributi prodotto composto
- Creazione sottoprodotto
- Scelta SKU per prodotto semplice
- Cancellazione associazione (prodotto-prodotto o prodotto-SKU)

---

## 📝 Note Importanti

- **Due versioni separate**: HTML puro e JavaScript devono essere applicazioni **completamente distinte**
- **Validazione doppia**: sempre client-side E server-side
- **Sicurezza**: impedire manipulation parametri tramite URL/form
- **Testing**: database con dati reali per testare tutti gli scenari
- **Performance**: ricordare che è una web app con potenzialmente molte gerarchie complesse

---

**Progetto per**: Tecnologie Informatiche per il Web  
**Data creazione documento**: 2026-05-01
