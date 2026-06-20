# TIW

Repository for project of "Tecnologie informatiche per il web".

Configuratore di prodotti realizzato in due versioni: pura HTML (`tiw-ssr`) e
SPA JavaScript (`tiw-spa`), entrambe deployate come WAR separati su Apache
Tomcat 10.1.

## Setup fotografie

Le fotografie delle SKU sono salvate in una **directory esterna condivisa** dalle
due versioni, in modo che un'immagine caricata da una versione sia visibile anche
dall'altra (le due app usano lo stesso database). A runtime i file vengono scritti
in `$CATALINA_BASE/tiw-foto` e serviti via HTTP dal context Tomcat `/foto`.

Servono due passi una tantum dopo aver configurato Tomcat. Prima di tutto, individua
il `CATALINA_BASE` del tuo Tomcat:

- **Tomcat da riga di comando**: è la cartella di installazione di Tomcat.
- **Tomcat gestito da Eclipse (WTP)**: NON è la cartella di installazione. Avvia il
  server e nella **Console** di Eclipse leggi, in cima al log, la riga
  `INFO: CATALINA_BASE: <percorso>`. È quel percorso (può essere la dir di Tomcat o
  una cartella di lavoro dentro il workspace, a seconda di "Server Locations").

### 1. Abilitare il context `/foto`

Monta `$CATALINA_BASE/tiw-foto` sull'URL `/foto`. Due modi equivalenti:

- **Tomcat standalone**: copia [`deploy/foto.xml`](deploy/foto.xml) in
  `$CATALINA_BASE/conf/Catalina/localhost/foto.xml`.

- **Eclipse (WTP)**: Eclipse rigenera `conf/Catalina/localhost/`, quindi conviene
  aggiungere il context dentro `<Host name="localhost" ...>` nel `server.xml` del
  server (lo trovi nel progetto **Servers** del workspace, oppure in
  `$CATALINA_BASE/conf/server.xml`):

  ```xml
  <Context docBase="${catalina.base}/tiw-foto" path="/foto" reloadable="true"/>
  ```

  Se Eclipse aveva già aggiunto un context `/foto` con un path assoluto vecchio
  (es. una `foto/` nella root del progetto), **sostituisci quel `docBase`** con
  `${catalina.base}/tiw-foto`: altrimenti Tomcat non parte
  (`The main resource set specified [...] is not a directory`).

  > **Attenzione (Eclipse/WTP):** Eclipse **non** usa direttamente il
  > `$CATALINA_BASE/conf/server.xml`. Tiene una copia "sorgente" nel progetto
  > **Servers** del workspace
  > (`Servers/Tomcat v10.1 Server at localhost-config/server.xml`) e la
  > **ripubblica** sopra `conf/server.xml` ad ogni avvio/publish. Se correggi solo
  > il file pubblicato, al primo republish la modifica viene **sovrascritta** e
  > Tomcat torna a fallire con lo stesso errore. Correggi quindi la riga
  > `<Context ... path="/foto" .../>` **nel file del progetto Servers**, poi tasto
  > destro sul server → **Clean...** e riavvia.

### 2. Creare la cartella e (opzionale) le immagini di esempio

```bash
mkdir -p "$CATALINA_BASE/tiw-foto"
cp database/foto-seed/* "$CATALINA_BASE/tiw-foto/"   # opzionale: immagini di esempio
```

La copia delle immagini è facoltativa: le SKU del seed (`database/data_test.sql`)
hanno `fotografia` NULL, quindi serve solo per avere immagini già pronte. La cartella
`$CATALINA_BASE/tiw-foto` viene comunque creata in automatico al primo upload se non
esiste.

### Verifica

Riavvia Tomcat e apri `http://localhost:8080/foto/<un-file-in-tiw-foto>`: deve
mostrare l'immagine. Se modifichi il JS/HTML e in pagina vedi ancora vecchi URL,
fai un **hard refresh** del browser (Cmd/Ctrl + Shift + R) per scaricare gli asset
aggiornati.

Nel database, la colonna `fotografia` contiene un path relativo nella forma
`foto/<nomefile>`; le view antepongono `/` per ottenere l'URL `/foto/<nomefile>`,
indipendente dal context (`/tiw-spa` o `/tiw-ssr`).
