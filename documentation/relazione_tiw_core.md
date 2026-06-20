# Relazione Dettagliata del Modulo `tiw-core`

Il modulo **`tiw-core`** costituisce il cuore logico e condiviso dell'intero progetto. È strutturato come un progetto Maven che compila in un archivio **JAR**. Questo approccio risolve elegantemente il problema della duplicazione del codice tra l'applicazione Server-Side Rendered (`tiw-ssr`) e la Single Page Application (`tiw-spa`), consentendo a entrambe di importare quest'unica fonte di verità per tutto ciò che riguarda l'accesso ai dati, i filtri di sicurezza trasversali e i modelli di dominio.

Di seguito è presentata un'analisi puntuale di ogni file e componente implementato nel progetto.

---

## 1. Configurazione del Progetto

### `pom.xml`
Il descrittore del progetto Maven definisce il `packaging` di tipo `jar`. 
Gestisce le dipendenze essenziali:
- **`jakarta.servlet-api`** (`provided`): per poter compilare i filtri Servlet senza portarli a runtime (li fornirà Tomcat).
- **`jbcrypt`**: per la verifica e l'hashing sicuro delle password.
- **`jackson-databind`**: per la gestione della serializzazione e deserializzazione in formato JSON (fondamentale per mappare il polimorfismo sui tipi `Prodotto`).
- **Librerie di Test** (`junit-jupiter` e `mysql-connector-j` in scope `test`).

---

## 2. Data Access Objects (Package `it.polimi.tiw.dao`)
In questo package risiede tutta la logica di accesso al database MySQL tramite chiamate JDBC pure (Prepared Statements). I DAO non gestiscono direttamente l'apertura/chiusura della connessione, ma la ricevono nel costruttore per permettere transazionalità ai livelli superiori.

### `ConfigurazioneDAO.java`
Gestisce le operazioni CRUD (Create, Read, Update, Delete) relative alla tabella `configurazione` e `configurazione_dettaglio`.
- **Implementazione**: 
  - I metodi di lettura/modifica/eliminazione proteggono la proprietà: ogni operazione ha una clausola `WHERE cliente_username = ?` per impedire accessi non autorizzati incrociati.
  - Sfrutta `Statement.RETURN_GENERATED_KEYS` durante gli inserimenti per associare i dettagli (scelte SKU) al corretto identificatore di testata.
  - Implementa il salvataggio dei dettagli in batch (`inserisciDettagliBatch`) garantendo che al salvataggio venga eseguito uno *Snapshotting* del prezzo corrente (evitando che sbalzi futuri del catalogo cambino i vecchi preventivi).
  - Include un metodo di pulizia automatica `eliminaConfigurazioniPerComponente`, utile al mantenimento dell'integrità referenziale quando un fornitore cancella un pezzo.

### `ProdottoDAO.java`
Il DAO più corposo, responsabile di interfacciarsi con un modello gerarchico ad albero di prodotti (Semplici e Composti).
- **Implementazione**:
  - Fornisce operazioni CRUD classiche (`insertSemplice`, `insertComposto`, `updateProdotto`).
  - Utilizza ampiamente **Common Table Expressions ricorsive (CTE)** in SQL (`WITH RECURSIVE`) per navigare la gerarchia:
    - `calcolaLivello`: Calcola la profondità di un nodo dall'alto.
    - `calcolaProfondita`: Calcola la profondità del sotto-albero dal nodo al livello foglia.
    - `verificaAciclicita`: Impedisce loop infiniti (es. A -> B -> C -> A) prima di confermare parentele.
  - Implementa il salvataggio di un intero albero atomico (`insertTree`) all'interno di una transazione, disabilitando temporaneamente l'`autoCommit`.
  - Fornisce strumenti per la ricerca full-text combinata tramite `LIKE` su nomi e descrizioni (`search`).

### `SKUDAO.java`
Gestisce la tabella `sku`, contenente le varianti fisiche effettive per i prodotti semplici.
- **Implementazione**: Permette il recupero del listino prezzi (`getPrezzoReale`), la modifica, e un metodo `eliminaDefinitivamente` transazionale che fa "pulizia" di vecchie configurazioni prima di rimuovere la SKU, per evitare che saltino i vincoli `RESTRICT` del database.

### `UtenteDAO.java`
Molto snello, ha lo scopo di verificare l'autenticazione.
- **Implementazione**: Il metodo `checkCredentials` estrae la riga utente in base allo username, e sfrutta `BCrypt.checkpw` per verificare che la password fornita in chiaro corrisponda all'hash nel database. Se il check passa, non restituisce tutta l'entità, ma solo il DTO della sessione (`UtenteSessionDTO`).

---

## 3. Data Transfer Objects (Package `it.polimi.tiw.dto`)

> [!NOTE]
> I DTO in questo progetto sono essenziali per isolare la logica UI dalle vere e proprie Entity del DB ed evitare leak di dati critici come gli hash delle password.

### `UtenteSessionDTO.java`
Implementato tramite un **Java `record`** immutabile. 
Viene inserito nella `HttpSession`. Ha una doppia finalità protettiva:
1. Impedisce scalate di privilegio accidentali poiché lo stato interno non può essere manipolato via set.
2. Contiene solo campi essenziali (`username`, `nome`, `cognome`, `ruolo`), omettendo la password hashata. Questo minimizza l'ingombro di RAM (~200 byte invece di caricare intere classi collegate) ed elimina rischi se la memoria viene inavvertitamente prelevata.

### `DettaglioDTO.java` e `VoceConfigurazioneDTO.java`
- **`DettaglioDTO`**: Piccolo raccoglitore per i dati grezzi necessari durante le INSERT batch nelle tabelle ponte (`idProdotto`, `idSku`, e il `prezzoUnitarioCongelato`).
- **`VoceConfigurazioneDTO`**: Funziona come aggregatore più complesso per il livello di vista, contenendo l'intero oggetto `SKU` più il prezzo storico, per visualizzare il riepilogo nella UI con le immagini e le descrizioni.

---

## 4. Filtri di Sicurezza (Package `it.polimi.tiw.filter`)

Tutta la logica di sicurezza, validazione di autenticazione e vulnerabilità web è centralizzata nei Filtri Servlet. Poiché il core è condiviso, gli stessi filtri agiscono e proteggono sia il lato SSR che le API della SPA.

### `AccessControlFilter.java`
- Controlla tutte le richieste. Accetta in whitelist solo i path statici (`/static/`) e il login.
- Controlla se nella sessione è presente `UtenteSessionDTO`. Se assente, rimanda alla login (se è navigazione browser) o sputa un `401 Unauthorized` in formato JSON (se intercetta un prefisso API, comodo per le chiamate AJAX della SPA).

### `RoleFilter.java`
- Implementa l'Autorizzazione (Autz). Le aree di gestione sono strettamente divise in `/fornitore/` (o `/api/fornitore/`) e `/cliente/` (o `/api/cliente/`).
- Legge l'Enum del ruolo dal session DTO e reindirizza con 403 Forbidden se un utente prova ad accedere allo spazio dell'altro ruolo.

### `CsrfFilter.java`
- Implementa il formidabile **Synchronizer Token Pattern** contro gli attacchi Cross-Site Request Forgery.
- **Funzionamento**: In fase di richieste `GET`, genera un token a 32-byte criptograficamente sicuro tramite `SecureRandom` in base64 URL-safe, memorizzandolo in sessione e rendendolo disponibile alle view.
- Per qualsiasi richiesta "mutante" (`POST`, `PUT`, `DELETE`), ad esclusione della sola API di `/login` che avviene pre-sessione, cerca e confronta che il token proveniente dal client (da un parametro form, o dall'header `X-CSRF-Token` molto usato nella SPA) coincida in tempo-costante (`MessageDigest.isEqual`) con quello salvato nel server.

---

## 5. Modello di Dominio (Package `it.polimi.tiw.model`)

L'applicazione fa largo uso del polimorfismo, mappando le relazioni architetturali su classi logiche.

### `Prodotto.java` (Astratta), `ProdottoComposto.java`, `ProdottoSemplice.java`
Implementano il **Composite Pattern** per formare strutture gerarchiche complesse:
- **`Prodotto`**: Classe base. Tramite le annotazioni di Jackson (`@JsonTypeInfo`, `@JsonSubTypes`) informa il deserializzatore JSON di istanziare correttamente gli oggetti figlio basandosi sul parametro "tipo". Contiene campi condivisi come id, nome e fasce di prezzo calcolate.
- **`ProdottoComposto`**: Un nodo che può avere a sua volta dei figli (sia composti che semplici). Implementato tramite una `List<Prodotto> figli`.
- **`ProdottoSemplice`**: Il nodo foglia dell'albero di business. Non contiene altri prodotti, ma contiene una lista di opzioni fisiche alternative (`List<SKU> skus`).

### `SKU.java` (Stock Keeping Unit)
Modella il pezzo "reale" e la variante del componente. Contiene l'effettivo prezzo statico, il riferimento all'URL fotografico e i dati tecnici che il cliente sceglie.

### `Configurazione.java`
Entità principale per il cliente: traccia il prodotto radice che ha iniziato a comporre, i timestamp (generati lato DB e riflessi in read), l'username proprietario, il nome custom dato alla "build" e il totale di spesa precalcolato.

### `ElementoCatalogo.java`
Una classe ausiliaria usata esclusivamente come risultato flat per la "Search Bar". Mappa sia SKU che Prodotti con un'interfaccia di visualizzazione in un'unica griglia omogenea.

### File Placeholder / Vuoti (`Utente.java`)
Esiste una classe `Utente.java` attualmente vuota. Questa scelta suggerisce che la gestione dell'utente è stata snellita delegando tutta la rappresentazione interna al database e trasportando a livello Java unicamente i DTO minimali (visti sopra).

---

## 6. Utilities (Package `it.polimi.tiw.utils`)

### `UserRole.java`
L'Enumeration che definisce saldamente i permessi in `CLIENTE` o `FORNITORE`. Contiene un metodo `fromString()` che normalizza con fallback qualsiasi stringa dal DB (accetta es. 'client', 'supplier') in uno dei due enum stretti.

### `ConnectionFactory.java`
Si occupa unicamente di prelevare dal `ServletContext` i parametri di connettività al database (caricati di norma all'avvio dal `web.xml`) e istanziare una `Connection` attiva tramite `DriverManager`.

### Placeholder Vuoti (`PasswordUtils.java`, `SessionConstants.java`)
Sono state inizializzate per future espansioni, al momento la logica di `PasswordUtils` è confluita usando direttamente le librerie BCrypt all'interno del DAO, e le costanti di sessione sono mantenute staticamente all'interno degli specifici DTO (`UtenteSessionDTO.SESSION_KEY`).

---

## 7. Testing (Package `src/test/java/.../dao`)

### `ProdottoDAOTest.java`
Un'attenta implementazione TDD (Test Driven Development) usata per verificare le complesse query SQL ricorsive scritte in MySQL8+.
- **Setup**: Sfrutta una connessione locale al database bloccando l'auto-commit. Al termine di ogni metodo, richiama il `rollback()` garantendo la pulizia automatica del database (il DB test e la sua gerarchia resta sempre incontaminato tra i singoli run).
- Esegue unit testing assertivi sull'aggiunta di cicli (`testVerificaAciclicita`), sul calcolo corretto della profondità gerarchica e sul conteggio delle SKU.

---
> [!TIP]
> **Scelta Architetturale**: Mantenere tutto il DAO e il model in un JAR esterno centralizza il codice SQL evitando desincronizzazioni fatali durante lo sviluppo su multi-applicativo. Tutte le istanze che utilizzano `tiw-core` otterranno le medesime query e le medesime logiche protettive (AccessControl, Role e CSRF), senza che un modulo possa implementare inavvertitamente difese di sicurezza più leggere di un altro.
