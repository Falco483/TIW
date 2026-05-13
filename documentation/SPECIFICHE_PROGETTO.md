# Progetto n.2: Configuratore di Prodotto

## Indice
1. [Regole di Svolgimento](#regole-di-svolgimento)
2. [Entità, Fasi e Ruoli](#entità-fasi-e-ruoli)
3. [Modalità di Accesso](#modalità-di-accesso)
4. [Vincoli Espliciti](#vincoli-espliciti)
5. [Interfacce](#interfacce)
6. [Aspetti Comuni](#aspetti-comuni)

---

## Regole di Svolgimento

### Presentazione e Valutazione

La valutazione del colloquio online tramite MS Teams è individuale e richiede:

- L'invio almeno **tre giorni prima** della data di presentazione della documentazione del progetto.
- La redazione della documentazione secondo gli esempi di esercizi mostrati durante le lezioni e le esercitazioni già disponibili nel sito WeBeep del corso.
- L'effettuazione di una dimostrazione del progetto, **sia nella versione HTML puro sia nella versione con JavaScript**.
- La discussione del codice con eventuali domande sulle motivazioni della progettazione e della codifica.

Il colloquio avviene in contemporanea per tutti i membri del gruppo. Si richiede la presenza online **5 minuti prima** dell'ora concordata. Il colloquio avviene mediante chiamata su MS Teams da parte del docente.

- La dimostrazione richiede la preparazione di una base di dati con contenuto sufficiente alla dimostrazione di **TUTTI i possibili scenari di uso** dell'applicazione.
- Si richiede di sapere utilizzare le funzioni di zoom dell'ambiente di sviluppo e del debugger del browser per consentire la visualizzazione ottimale del codice da remoto e di impostare il **tema chiaro** per entrambi (sfondo bianco).
- Si consiglia di provare con un collega il funzionamento di MS Teams (audio, condivisione dello schermo, leggibilità).
- Si consiglia di tenere a portata di mano l'elenco delle credenziali degli utenti utili per la dimostrazione. L'accesso alla base di dati per reperire le credenziali necessarie comporta una perdita di tempo inutile ed è valutato negativamente.

### Penalità

- Il non raggiungimento della sufficienza comporta il rifacimento dell'esame con il cambio del progetto.
- Il cambio della data concordata per il colloquio non giustificato da gravi motivi comporta una penalità di **due punti** sulla valutazione dell'esame.
- La mancata presenza al colloquio non giustificata da gravi motivi comporta il rifacimento dell'esame con il cambio del progetto.
- La presenza di altri esami nella data del colloquio o nei giorni vicini, in assenza di sovrapposizione di orario, non costituisce motivo valido per lo spostamento della data concordata. L'eventuale spostamento dell'orario nella stessa data per evitare un'eventuale sovrapposizione è ammissibile, ma deve essere concordato con almeno una settimana di anticipo.

### Commenti Generali

- Le versioni pure HTML e JavaScript sono da realizzarsi come **applicazioni web distinte**.
- Il controllo di validità dei parametri deve essere fatto sempre **sia lato client sia lato server**.
- A fronte di errori nel salvataggio di una form, l'applicazione deve ricaricare la pagina contenente la form **preservando i valori inseriti** dall'utente e segnalando i valori mancanti o errati.
- Non si deve consentire a un utente di fare operazioni che il suo ruolo non permette.
- Non si deve consentire a un utente malintenzionato di violare la sicurezza dell'applicazione mediante l'invio di valori scorretti dei parametri.
- Eventuali funzioni non richieste di gestione dei dati (ad esempio, modifica o cancellazione) possono essere realizzate se comode per il testing ma sono opzionali e non valutate.
- L'utilizzo di librerie aggiuntive è consentito, ma deve essere documentato nella documentazione del progetto e si deve essere in grado di spiegarne il funzionamento.
- **La versione pure HTML non può utilizzare codice JavaScript.**

---

## Entità, Fasi e Ruoli

Un prodotto composto definisce un articolo configurabile come una struttura gerarchica comprendente diverse parti. Ad esempio, un PC configurabile comprende sistema di elaborazione, sistema di memoria, sistema di archiviazione, sistema di raffreddamento, case e alimentazione. Il sistema di elaborazione comprende scheda madre, CPU, memoria RAM e scheda grafica. Il sistema di memoria comprende primo disco fisso, secondo disco fisso, unità RAID, unità DVD.

### Prodotto Composto
- **Attributi**: codice (intero unico), nome, descrizione, fascia di prezzo (prezzo minimo e prezzo massimo)
- **Composizione**: associato a uno o più sottoprodotti (semplici o composti)

### Prodotto Semplice
- **Attributi**: codice (intero unico), nome
- **Associazione**: collegato ai diversi SKU che lo possono realizzare (es. la CPU può essere AMD Ryzen 5 9500F, AMD Ryzen 7 7700X, ...)

### SKU (Stock Keeping Unit)
- **Attributi**: codice (numero intero), nome, fotografia, descrizione tecnica, prezzo

### Ruoli
- **Fornitore**: crea le SKU e definisce il modello gerarchico di un prodotto composto. Crea il prodotto composto di più alto livello, aggiunge i sottoprodotti composti e semplici e associa ai prodotti semplici le SKU corrispondenti.
- **Cliente**: definisce la configurazione di prodotto che desidera acquistare a partire da un prodotto composto. Seleziona la SKU desiderata per ogni prodotto semplice facente parte del prodotto composto.

---

## Modalità di Accesso

L'applicazione prevede l'accesso tramite credenziali (login con nome utente e password). Ad ogni ruolo corrisponde un'interfaccia diversa in base alle azioni che l'utente con quel ruolo può svolgere. La base di dati contiene l'anagrafica degli utenti. Si assuma che **clienti e fornitori siano sempre utenti diversi** (nessun utente può svolgere due ruoli contemporaneamente).

---

## Vincoli Espliciti

1. **Azienda unica**: tutti i prodotti appartengono ad una sola azienda. Non devono essere gestiti prodotti di aziende differenti.
2. **SKU obbligatoria**: ogni prodotto semplice deve avere almeno una SKU.
3. **Configurazione completa**: ogni configurazione deve selezionare una SKU per ogni prodotto semplice.
4. **Prezzi ≥ 0**.
5. **Gerarchia aciclica**: la struttura dei prodotti composti deve essere aciclica. Non è consentito inserire come sottoprodotto un prodotto che contenga già, direttamente o indirettamente, il prodotto corrente.
6. **Profondità massima**: la profondità massima della gerarchia dei prodotti è di **3 livelli sotto il prodotto di primo livello** (ovvero 4 livelli, compreso il primo).
7. **Padre unico**: un prodotto semplice o composto può appartenere a un solo prodotto composto padre.
8. **SKU condivisa**: una SKU può essere associata a uno o più prodotti semplici.
9. **Codice unico**: ogni prodotto, che sia semplice o composto, ha un codice diverso da quello di tutti gli altri prodotti.
10. **Eliminazione a cascata**: l'eliminazione da parte del fornitore di un qualsiasi componente di un prodotto comporta l'eliminazione di eventuali configurazioni create dall'utente che contengano anche solo un componente eliminato.

---

## Interfacce

### Interfaccia del Fornitore

#### Pagina HOME FORNITORE

A seguito del login, il fornitore accede alla pagina HOME FORNITORE che contiene:
- Una form per creare una SKU
- Una form per creare un prodotto composto
- Una form per creare un prodotto semplice

**Form per creare una SKU:**
- Campi: codice, nome, fotografia, descrizione tecnica, prezzo
- Bottone **SALVA**: inserisce la SKU nella base di dati permanentemente

**Form per creare un prodotto semplice:**
- Campi: codice, nome
- Associazione SKU: spunta di una o più checkbox da un elenco delle SKU, **ordinato per codice delle SKU decrescente**
- Bottone **SALVA**: inserisce il prodotto semplice e le associazioni alle SKU nel DB

**Form per creare un prodotto composto:**
- Campi: codice, nome, descrizione, fascia di prezzo (min e max)
- Associazione sottoprodotti: spunta di una o più checkbox da un elenco dei prodotti (semplici e composti), **ordinato per nome decrescente (case-sensitive)** — solo prodotti precedentemente creati ma **non ancora utilizzati in altri prodotti**
- Vincolo: profondità massima 3 livelli sotto il prodotto di primo livello
- Bottone **SALVA**: inserisce il prodotto composto e le associazioni ai sottoprodotti nel DB

**Visualizzazione dopo il salvataggio:**
- **SKU**: i soli valori degli attributi della nuova SKU
- **Prodotto semplice**: i valori degli attributi e la lista delle SKU che lo realizzano
- **Prodotto composto**: i valori degli attributi e una **lista nidificata multilivello** che mostra ricorsivamente tutti i componenti del prodotto composto e le SKU associate con i prodotti semplici

**Modifica di un prodotto:**
La modifica di un prodotto semplice o composto avviene mediante **cancellazione del prodotto** e successiva **creazione di un nuovo prodotto** con ugual codice e nome e riassegnazione di sottoprodotti o SKU.

#### Pagina RICERCA PRODOTTI

Contiene una form per la ricerca case-insensitive di prodotti e SKU:
- Un solo campo di input
- Bottone **RICERCA**
- Validazione: il campo di input non deve essere vuoto
- Ricerca **case-insensitive** applicata a:
  - Campi nome e descrizione dei prodotti
  - Campi nome e descrizione tecnica delle SKU

**Risultati:**
- Presentati come un elenco cliccabile
- Click su un elemento: visualizzazione di tutti gli attributi e di tutte le associazioni dell'oggetto (SKU, prodotto semplice o composto) con le stesse modalità descritte per la presentazione di un oggetto appena creato

**Link disponibili nei risultati:**
- **RIMUOVI** accanto a ogni SKU di un prodotto semplice: elimina la relazione tra il prodotto semplice e la SKU → aggiornamento DB + ricarico HOME FORNITORE + aggiornamento dati
- **RIMUOVI** accanto a ogni sottoprodotto nella lista nidificata: elimina la relazione tra il sottoprodotto e il prodotto composto che lo contiene → aggiornamento DB + ricarico HOME FORNITORE + aggiornamento dati
- **ELIMINA** accanto a ogni oggetto (prodotto composto, prodotto semplice, SKU): elimina l'oggetto e i suoi eventuali sottoprodotti e SKU dalla base di dati → elimina anche tutte le associazioni con i prodotti di cui l'oggetto fa eventualmente parte → ricarico HOME FORNITORE + aggiornamento dati

#### Versione JavaScript — Interfaccia Fornitore

- L'interfaccia deve essere realizzata in **una sola pagina**. L'invio di richieste al server è gestito in modo asincrono e provoca l'aggiornamento parziale dell'interfaccia limitatamente ai soli dati che variano.

**SKU:**
- La creazione avviene tramite una form.
- A seguito della pressione del bottone **SALVA SKU**, la pagina mostra i dati della nuova SKU.
- Il click sul valore di un attributo della SKU rende il valore editabile mediante un campo di input.
- Il nuovo valore è salvato nella base di dati automaticamente a seguito dell'**evento di uscita del mouse** dal campo di input.

**Prodotto Composto:**
- L'utente crea il prodotto di primo livello mediante una form in cui inserisce il valore degli attributi.
- A seguito della pressione del bottone **SALVA PRODOTTO** e alla creazione a lato client del prodotto di primo livello, la pagina ne mostra i dati.
- Il click sul valore di un attributo del prodotto di primo livello rende il valore editabile mediante un campo di input.
- Il nuovo valore è salvato nella base di dati mediante la pressione del bottone **SALVA PRODOTTO**.
- A fianco del prodotto compaiono un bottone **+**, un bottone **-** e un bottone **-\***.

**Bottone + accanto a un prodotto composto:**
- Produce la visualizzazione di un menu con le scelte "sottoprodotto composto" e "sottoprodotto semplice".
- Scelta "sottoprodotto composto": crea un prodotto composto come sottoelemento dell'elemento corrente.
- Scelta "sottoprodotto semplice": crea un prodotto semplice come sottoelemento — inserimento di codice e nome in campi di input e scelta delle SKU da un menu a selezione multipla.
- In entrambi i casi, il sottoprodotto viene mostrato sotto il prodotto in modo da formare una **visualizzazione a lista nidificata**.
- Il click sul valore di un attributo di un prodotto semplice o composto nidificato rende il valore editabile mediante un campo di input. La modifica è salvata nella base di dati mediante la pressione del bottone **SALVA PRODOTTO**.

**Bottone + accanto a un prodotto semplice:**
- Produce la visualizzazione di un menu con le scelte "Nuova SKU" e "SKU esistente".
- Scelta "nuova SKU": apparizione di una form per la creazione di una nuova SKU.
- Scelta "SKU esistente": apparizione di una form per la scelta di una o più SKU da un elenco a scelta multipla di tutte le SKU esistenti.
- In entrambi i casi, le SKU associate sono mostrate sotto il prodotto semplice in modo da formare una visualizzazione a lista nidificata.
- La modifica è salvata nella base di dati mediante la pressione del bottone **SALVA PRODOTTO**.

**Bottone - accanto a un prodotto:**
- Appare solo quando esiste un prodotto padre.
- Produce la **cancellazione della relazione** tra il sottoprodotto e il prodotto padre, ma lascia intatto il prodotto figlio nella base di dati.
- Il prodotto figlio diventa un prodotto di primo livello.
- La modifica è salvata nella base di dati mediante la pressione del bottone **SALVA PRODOTTO**.

**Bottone -\* accanto a un prodotto:**
- Produce la **cancellazione del prodotto stesso** e dei suoi sottoprodotti ricorsivamente.
- Le SKU associate vengono eliminate solo se non risultano associate ad altri prodotti semplici.
- La modifica è salvata nella base di dati mediante la pressione del bottone **SALVA PRODOTTO**.

**Bottone - accanto a una SKU:**
- Produce la cancellazione della relazione tra SKU e prodotto.

**Bottone -\* accanto a una SKU:**
- Produce la cancellazione della SKU dalla base di dati.
- La modifica è salvata nella base di dati mediante la pressione del bottone **SALVA PRODOTTO**.

**Regola generale di salvataggio:**
- La creazione e la modifica di un prodotto composto devono avvenire **inizialmente solo nel client**.
- Quando l'inserimento di tutti i dati è completo, il bottone **SALVA PRODOTTO** permette la registrazione permanente del prodotto composto nella base di dati.
- Il tentativo di creare un prodotto composto mancante di SKU per qualcuno dei prodotti semplici produce un **messaggio di errore**, ma non la perdita dei dati inseriti a lato client fino a quel momento.

**Ricerca Prodotti:**
- La funzione della pagina RICERCA PRODOTTI è realizzata all'interno della singola pagina dell'applicazione.
- Dopo la visualizzazione è possibile modificare gli attributi dei prodotti e delle SKU visualizzati, eliminare associazioni e eliminare oggetti dalla base di dati con le stesse modalità disponibili durante la creazione di un prodotto composto.

**Funzionalità opzionale (non richiesta per la sufficienza):**
- **Undo/redo delle operazioni del fornitore lato client**: le azioni soggette ad undo/redo sono: la creazione di un prodotto composto, l'inserimento del valore degli attributi di un prodotto composto, la creazione di un sottoprodotto di un prodotto, la scelta di una SKU per un prodotto semplice, la cancellazione di un'associazione tra prodotti o tra prodotti e SKU.

---

### Interfaccia del Cliente

#### Pagina HOME CLIENTE

- Elenca **tutti i prodotti composti di primo livello** in ordine alfabetico di nome **decrescente**.
- Se i prodotti sono più di 10, la pagina mostra inizialmente i primi 10 e i bottoni **PRECEDENTI** e **SUCCESSIVI** permettono di scorrere l'elenco visualizzando 10 prodotti per volta.
- Tali bottoni devono comparire **solo se esiste** un insieme precedente o successivo di prodotti.

#### Pagina SCELTA SKU

- Selezionando un prodotto di primo livello compare la pagina SCELTA SKU che riporta il prodotto di primo livello selezionato e al di sotto di questo l'elenco dei prodotti di secondo livello eventualmente presenti.
- Selezionando un prodotto di secondo livello compare al di sotto di questo l'elenco dei prodotti di terzo livello eventualmente presenti, e così via.
- A fianco di ogni prodotto semplice, a **qualunque livello** esso si collochi, compare un **menu a tendina** che permette di scegliere la SKU per quel prodotto.
- Una volta selezionate tutte le SKU necessarie per la configurazione il cliente può usare un bottone **SALVA** per memorizzare la propria configurazione nella base di dati.
- Il salvataggio richiede l'inserimento di un **nome** per la configurazione.
- La configurazione è associata al cliente che l'ha creata, alla **data di creazione** e al **prezzo totale** calcolato come somma dei costi delle SKU selezionate.

**Esempio di visualizzazione:**
```
PC Desktop
  Case >|Case-sku1|
         |Case-sku2|
         ...
  Sistema di elaborazione
    CPU >|CPU-sku1|
         ...
    GPU >|GPU-sku1|
         ...
    RAM >|RAM-sku1|
         ...
[SALVA]
```

**Gestione errori:**
- Se il cliente preme il bottone SALVA ma alcune SKU non sono state scelte: l'applicazione notifica un errore e ripropone la pagina SCELTA SKU con le **selezioni fatte in precedenza**.
- Se il salvataggio va a buon fine: l'applicazione mostra la pagina DETTAGLIO CONFIGURAZIONE.

#### Pagina DETTAGLIO CONFIGURAZIONE

- Mostra le SKU selezionate con i rispettivi dati e il **prezzo totale** della configurazione.

#### Pagina LE MIE CONFIGURAZIONI

- Mostra tutte le configurazioni create dall'utente in **ordine di data di creazione decrescente**.
- Azioni disponibili:
  - **Cancella**: elimina la configurazione
  - **Clona**: duplica la configurazione
  - **MODIFICA**: apre la pagina SCELTA SKU per quella configurazione, permette di modificare la selezione delle SKU e salvare la versione modificata, aggiornando la **data di ultima modifica**.

#### Versione JavaScript — Interfaccia Cliente

- L'interfaccia deve essere realizzata in **una sola pagina**. L'invio di richieste al server è gestito in modo asincrono e provoca l'aggiornamento parziale dell'interfaccia limitatamente ai soli dati che variano.

---

## Aspetti Comuni a Tutte le Interfacce

- Tutte le interfacce contengono un link **LOGOUT** che annulla la sessione dell'utente e riporta alla pagina di login.
- Nelle interfacce con più pagine, ogni pagina diversa dalla home contiene un link **HOME** che riporta alla home page dell'interfaccia.
- Ogni pagina HOME riporta un **messaggio di saluto** che mostra il nome e cognome dell'utente.

---

**Progetto per**: Tecnologie Informatiche per il Web — Politecnico di Milano  
**Versione specifiche**: finale (2025-26)
