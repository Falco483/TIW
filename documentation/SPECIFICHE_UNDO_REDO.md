# Specifiche Funzionali — Undo/Redo delle operazioni del Fornitore (versione SPA)

## 1. Scopo e contesto

### 1.1 Testo delle specifiche di progetto

> Undo/redo delle operazioni del fornitore lato client: l'inserimento a lato client
> (prima dell'aggiornamento della base di dati) dei dati di un prodotto composto
> consente le operazioni di undo e di redo. Le azioni soggette ad undo/redo sono: la
> creazione di un prodotto composto, l'inserimento del valore degli attributi di un
> prodotto composto, la creazione di un sottoprodotto di un prodotto, la scelta di una
> SKU per un prodotto semplice, la cancellazione di un'associazione tra prodotti o tra
> prodotti e SKU.

### 1.2 Interpretazione

La funzionalità è **esclusivamente lato client** e opera **prima** che le modifiche
vengano persistite sul database. Il fornitore costruisce/modifica l'albero di un
prodotto composto nell'editor dell'albero ("Tree Editor"); tutte le operazioni
restano in uno stato pendente in RAM finché non viene premuto il pulsante di
salvataggio (`Salva` / `syncTree`). Fino a quel momento il fornitore deve poter
**annullare** (undo) e **ripristinare** (redo) le operazioni effettuate.

Questo documento definisce **cosa** deve fare il sito (comportamento osservabile,
edge case), non **come** implementarlo.

### 1.3 Glossario

| Termine | Significato |
|---|---|
| **Operazione undoable** | Una delle azioni elencate al §2 che modifica lo stato pendente dell'albero. |
| **Stato pendente** | L'insieme delle modifiche effettuate lato client e non ancora salvate sul DB. |
| **Undo** | Annulla l'ultima operazione undoable, riportando lo stato pendente a com'era prima di essa. |
| **Redo** | Ri-applica l'ultima operazione annullata con undo. |
| **Checkpoint / commit** | Il momento in cui un'operazione completata viene registrata nella cronologia come unità annullabile. |
| **Sessione di editing** | L'intervallo che va dall'apertura dell'editor di un prodotto composto fino al salvataggio su DB o all'abbandono dell'editor. |

## 2. Operazioni soggette a Undo/Redo

Le seguenti operazioni, e **solo** queste, sono soggette a undo/redo. Ognuna costituisce
**una singola unità annullabile** (un undo annulla l'intera operazione, non un suo
frammento).

| # | Operazione | Descrizione | Azione pendente correlata |
|---|---|---|---|
| A | **Creazione di un prodotto composto** | Il fornitore crea un nuovo prodotto di tipo `COMPOSTO` (radice dell'albero in costruzione). | `CREATE_NODE` (COMPOSTO, radice) |
| B | **Inserimento valore attributi di un prodotto composto** | Il fornitore inserisce/modifica gli attributi (nome, codice, ecc.) di un nodo composto. | `UPDATE_NODE` |
| C | **Creazione di un sottoprodotto** | Il fornitore aggiunge un figlio (SEMPLICE o COMPOSTO) a un nodo composto esistente. | `CREATE_NODE` (con `parentId`) |
| D | **Scelta di una SKU per un prodotto semplice** | Il fornitore associa una SKU (esistente o creata ex-novo) a un nodo semplice. | `ADD_SKU` / `CREATE_SKU` |
| E | **Cancellazione di un'associazione** | Il fornitore rimuove l'associazione prodotto→prodotto (scollegamento figlio) **oppure** prodotto→SKU. | `UNLINK_NODE` / `UNLINK_SKU` |

### 2.1 Operazioni esplicitamente NON soggette a Undo/Redo

Queste operazioni **non** devono essere annullabili tramite undo/redo, perché agiscono
direttamente sul database o esulano dal contesto della sessione di editing:

- **Eliminazione definitiva di una SKU dal catalogo** (`deleteSku`): chiama subito il
  DB e ha effetti a cascata (configurazioni cliente). Una volta eseguita, esce dallo
  scope dello stato pendente. *Nota: la traccia parla di "cancellazione di un'associazione",
  non di eliminazione fisica.*
- **Eliminazione definitiva di un prodotto dal DB** (`deleteProdotto`).
- **Salvataggio dell'albero** (`syncTree`): è l'azione che chiude la sessione di
  editing (vedi §6).
- Operazioni della sezione di **ricerca** e di **gestione catalogo SKU** indipendenti
  dall'editor dell'albero.
- Navigazione tra sezioni, login/logout.

## 3. Modello concettuale dello stato

1. Esiste **un'unica fonte di verità** in RAM per l'albero in costruzione (il modello
   del prodotto composto e dei suoi discendenti/SKU).
2. Ogni operazione undoable produce una **transizione di stato** sul modello.
3. La cronologia è modellata come due pile:
   - **Pila Undo (passato)**: stati precedenti, dal più recente al più vecchio.
   - **Pila Redo (futuro)**: stati annullati e ri-ripristinabili.
4. La vista (DOM del Tree Editor) è sempre una **funzione dello stato corrente**: dopo
   ogni undo/redo la UI deve riflettere esattamente lo stato ripristinato.

## 4. Comportamento funzionale richiesto

### 4.1 Registrazione di un'operazione (commit)

- **R1** — Al completamento di una qualsiasi operazione del §2, lo stato precedente
  deve essere registrato nella pila Undo come nuovo checkpoint.
- **R2** — Ogni nuova operazione undoable **svuota la pila Redo**: dopo aver compiuto
  una nuova azione, non è più possibile fare redo delle azioni precedentemente annullate.
- **R3** — Un'operazione che, pur essendo di un tipo undoable, **non modifica nulla**
  (es. inline edit che reimposta lo stesso identico valore) **non** deve generare un
  checkpoint (evita undo "a vuoto"). Vedi §5.2.

### 4.2 Undo

- **R4** — L'azione Undo riporta lo stato pendente e la vista esattamente a com'erano
  **prima dell'ultima operazione undoable**.
- **R5** — Lo stato annullato viene spostato sulla pila Redo, così da poter essere
  ripristinato.
- **R6** — Se la pila Undo è vuota, l'azione Undo **non ha effetto** e il relativo
  controllo è disabilitato (vedi §7).
- **R7** — Più Undo consecutivi annullano le operazioni in ordine cronologico inverso
  (LIFO), fino a riportare l'albero allo stato iniziale di apertura della sessione.

### 4.3 Redo

- **R8** — L'azione Redo ri-applica l'ultima operazione annullata, riportando stato e
  vista a com'erano prima dell'undo corrispondente.
- **R9** — Se la pila Redo è vuota, l'azione Redo **non ha effetto** e il relativo
  controllo è disabilitato.
- **R10** — Più Redo consecutivi ripristinano le operazioni in ordine cronologico
  (fino all'esaurimento della pila Redo o finché non si compie una nuova operazione,
  vedi R2).

### 4.4 Coerenza vista/stato

- **R11** — Dopo ogni undo/redo la rappresentazione visiva dell'albero (nodi, figli,
  SKU associate, valori degli attributi nei campi editabili) deve corrispondere
  byte-per-byte allo stato logico ripristinato.
- **R12** — La coda di azioni pendenti che verrà inviata al server al salvataggio
  (`syncTree`) deve essere coerente con lo stato corrente dopo gli undo/redo: se
  un'operazione è stata annullata, la sua azione **non deve** essere inviata al server.

## 5. Comportamento per singola operazione

### 5.1 A — Creazione prodotto composto (radice)

- Crea il nodo radice COMPOSTO ⇒ checkpoint.
- **Undo**: rimuove il prodotto composto appena creato; l'editor torna allo stato
  "nessun albero in costruzione" (o allo stato precedente, se la sessione era già
  avviata).
- **Edge case**: se la radice viene annullata, anche tutte le operazioni discendenti
  diventano logicamente assenti. Poiché l'undo procede LIFO, l'utente avrà già
  annullato i figli prima di poter annullare la radice (la radice è la prima operazione,
  quindi l'ultima annullabile). Non deve essere possibile annullare la radice
  lasciando "orfani" figli ancora visibili.

### 5.2 B — Inserimento/modifica attributi del composto

- La modifica di un attributo (nome, codice, prezzo min/max, ecc.) tramite editing
  inline genera **un** checkpoint **al termine** dell'inserimento (es. perdita di
  focus del campo / `focusout`), **non** un checkpoint per ogni carattere digitato.
- **R3 applicata**: se il valore finale è identico a quello iniziale, nessun checkpoint.
- **Undo**: ripristina il valore precedente dell'attributo e lo riporta nel campo
  editabile.
- **Edge case — validazione**: se il valore inserito è invalido (vedi §8), l'operazione
  non deve essere registrata come checkpoint finché non è valida; un undo non deve mai
  ripristinare uno stato invalido proveniente da un input rifiutato.

### 5.3 C — Creazione sottoprodotto

- Aggiunge un nodo figlio a un nodo composto ⇒ checkpoint.
- **Undo**: rimuove il sottoprodotto creato e tutte le sue eventuali SKU/figli **creati
  nella stessa operazione**. (Se il sottoprodotto è stato creato vuoto e poi popolato
  con operazioni successive, l'undo della creazione del sottoprodotto avverrà solo dopo
  aver annullato — LIFO — le operazioni di popolamento.)
- **Edge case — profondità massima**: la creazione che violerebbe il limite di
  profondità (max 4 livelli totali / 3 livelli strutturali sotto la radice secondo la
  regola di dominio) è **rifiutata prima del checkpoint**, quindi non genera voce di
  cronologia. Un redo non deve poter ricreare un nodo che violerebbe la profondità (se
  nel frattempo lo stato è cambiato — vedi §5.6 sulla linearità della cronologia, che
  rende questo caso impossibile per costruzione).

### 5.4 D — Scelta SKU per prodotto semplice

- Associazione di SKU esistente (`ADD_SKU`) o creazione SKU ex-novo associata
  (`CREATE_SKU`) ⇒ checkpoint.
- **Undo**: rimuove l'associazione SKU↔prodotto semplice; la SKU torna disponibile
  nell'elenco selezionabile. Se era una SKU creata ex-novo (id temporaneo, non ancora
  sul DB), l'undo la rimuove completamente dallo stato pendente.
- **Edge case — SKU duplicata**: la regola di dominio "una SKU per prodotto semplice
  per configurazione / niente duplicati di associazione" deve essere verificata prima
  del checkpoint. Dopo un undo che rimuove un'associazione, la stessa SKU deve tornare
  associabile.

### 5.5 E — Cancellazione di un'associazione

Due sotto-casi, entrambi undoable:

- **E1 — prodotto→prodotto** (scollegamento di un figlio dal padre, `UNLINK_NODE`):
  - **Undo**: ripristina il legame padre-figlio e re-inserisce il sottoalbero nella
    posizione originale.
- **E2 — prodotto→SKU** (`UNLINK_SKU`):
  - **Undo**: ri-associa la SKU al prodotto semplice.
- **Edge case — distinzione scollega vs elimina**: solo lo **scollegamento**
  (rimozione dell'associazione, lo nodo/SKU continua a esistere come entità) è
  undoable. L'eliminazione definitiva dal DB **non** lo è (§2.1).
- **Edge case — ripristino posizione**: l'undo di uno scollegamento deve ripristinare
  l'elemento nella sua posizione precedente nell'albero (stesso padre, e idealmente
  stesso ordine tra i fratelli), non aggiungerlo "in coda".

### 5.6 Linearità della cronologia

- **R13** — La cronologia è **lineare**: ogni nuova operazione dopo uno o più undo
  cancella il ramo di redo (R2). Non esistono cronologie ad albero/ramificate.

## 6. Ciclo di vita della cronologia

- **R14 — Inizio sessione**: all'apertura dell'editor di un prodotto composto, le pile
  Undo e Redo sono **vuote**. Lo stato iniziale (albero appena caricato dal server o
  appena creato) è il punto a cui gli undo possono al massimo riportare.
- **R15 — Salvataggio riuscito**: dopo un `syncTree` andato a buon fine, le modifiche
  sono persistite; la cronologia Undo/Redo viene **azzerata** (non si può fare undo di
  modifiche già salvate sul DB tramite questo meccanismo client-side). I controlli
  Undo/Redo tornano disabilitati.
- **R16 — Salvataggio fallito**: se `syncTree` fallisce, lo stato pendente e la
  cronologia **restano intatti**, così l'utente può correggere e ritentare (o fare
  undo).
- **R17 — Cambio prodotto / abbandono editor**: aprendo un altro prodotto o
  abbandonando l'editor, la cronologia della sessione precedente viene scartata. Se ci
  sono modifiche pendenti non salvate, vedi §8 (avviso).

## 7. Comportamento dell'interfaccia (UI)

- **U1** — Devono essere presenti due controlli ben visibili e raggiungibili
  dall'interno dell'editor dell'albero: **Annulla (Undo)** e **Ripristina (Redo)**.
- **U2** — Il controllo Undo è **disabilitato** quando la pila Undo è vuota; il
  controllo Redo è **disabilitato** quando la pila Redo è vuota.
- **U3 (opzionale ma raccomandato)** — Scorciatoie da tastiera: `Ctrl/Cmd+Z` per Undo,
  `Ctrl/Cmd+Y` (o `Ctrl/Cmd+Shift+Z`) per Redo. Le scorciatoie **non** devono attivarsi
  mentre il focus è su un campo di testo in editing (per non interferire con l'undo
  nativo del campo).
- **U4 (opzionale)** — Feedback testuale che indichi quale operazione è stata
  annullata/ripristinata (es. "Annullato: creazione sottoprodotto").
- **U5** — Lo stato dei controlli (abilitato/disabilitato) deve aggiornarsi
  immediatamente dopo ogni operazione, undo e redo.

## 8. Edge case ed errori

- **EC1 — Undo/Redo a pila vuota**: nessun effetto, controllo disabilitato (R6, R9).
- **EC2 — Editing inline non valido**: input che viola le validazioni (campo
  obbligatorio vuoto, codice fuori formato, prezzo non numerico/negativo) viene
  rifiutato con messaggio d'errore e **non** genera checkpoint; lo stato resta quello
  valido precedente.
- **EC3 — Modifica identica**: re-inserire lo stesso valore non crea checkpoint (R3).
- **EC4 — Sequenza Undo → nuova operazione**: la pila Redo viene svuotata; non si può
  più ripristinare le operazioni annullate (R2/R13). La UI deve disabilitare Redo.
- **EC5 — Undo fino allo stato iniziale**: ulteriori Undo non hanno effetto; l'albero
  resta nello stato di apertura sessione.
- **EC6 — Operazioni con id temporanei**: nodi/SKU non ancora salvati usano id
  temporanei; gli snapshot devono preservarli affinché undo/redo restino coerenti e il
  successivo `syncTree` invii le azioni corrette.
- **EC7 — Interazione con eliminazione DB definitiva**: se durante la sessione l'utente
  esegue un'operazione non-undoable che tocca il DB (es. eliminazione definitiva SKU),
  la cronologia undo/redo riferita allo stato pendente deve restare **coerente**: le
  voci di cronologia che referenziano l'entità eliminata vanno invalidate/ripulite per
  evitare che un undo/redo tenti di ripristinare un'entità non più esistente.
  *(Raccomandazione: dato il rischio di incoerenza, è preferibile non mescolare nella
  stessa sessione operazioni undoable e operazioni distruttive sul DB.)*
- **EC8 — Abbandono con modifiche pendenti**: se l'utente lascia l'editor / chiude la
  pagina con operazioni pendenti non salvate (e quindi annullabili), mostrare un avviso
  di conferma per evitare perdita involontaria di lavoro.
- **EC9 — Salvataggio parziale impossibile**: il salvataggio invia l'intero stato
  pendente coerente con la cronologia corrente; non deve essere possibile salvare uno
  stato che include un'operazione successivamente annullata (R12).
- **EC10 — Limiti di dominio durante redo**: poiché la cronologia è lineare (R13), un
  redo ripristina sempre uno stato che era già valido al momento in cui era stato
  raggiunto; non può quindi reintrodurre violazioni (profondità, cicli, duplicati).

## 9. Vincoli di dominio sempre validi

Le regole di dominio del progetto restano in vigore in ogni momento, anche durante
undo/redo (gli stati salvati nella cronologia sono per costruzione stati già validati):

1. Profondità massima dell'albero: 4 livelli.
2. Nessun ciclo nelle relazioni padre-figlio.
3. Un prodotto ha al più un padre.
4. Almeno una SKU per ogni prodotto semplice (verificata al salvataggio).
5. Una sola SKU per prodotto semplice per configurazione (regola lato configurazione).

## 10. Criteri di accettazione (riassunto verificabile)

1. Creando un prodotto composto, poi un sottoprodotto, poi associando una SKU, tre
   Undo consecutivi riportano l'albero esattamente allo stato iniziale, in ordine
   inverso.
2. Dopo N Undo, N Redo riportano l'albero esattamente allo stato pre-undo.
3. Compiendo una nuova operazione dopo un Undo, il Redo risulta disabilitato e la
   nuova operazione è registrata.
4. Annullando uno scollegamento (prodotto→prodotto o prodotto→SKU), l'associazione
   viene ripristinata nella posizione originale.
5. L'editing inline di un attributo del composto è annullabile come singola unità (un
   Undo annulla l'intero inserimento, non singoli caratteri).
6. I controlli Undo/Redo sono disabilitati quando le rispettive pile sono vuote.
7. Dopo un salvataggio riuscito, Undo/Redo sono disabilitati (cronologia azzerata).
8. Lo stato inviato al server al salvataggio riflette sempre e solo le operazioni non
   annullate.
