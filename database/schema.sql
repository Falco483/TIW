-- =============================================================================
-- PROGETTO TIW — Configuratore di Prodotto
-- Script DDL DEFINITIVO — MySQL 8+ / MariaDB 10.5+
-- Fase 0/1 | Data: 2026-05-02
-- =============================================================================
--
-- ARCHITETTURA A DUE AREE:
--
--   AREA CATALOGO (Master Data)         AREA TRANSAZIONI (Transactional Data)
--   ┌────────────────────────┐          ┌──────────────────────────────┐
--   │  utente                │          │  configurazione              │
--   │  prodotto              │          │  configurazione_dettaglio    │
--   │  sku                   │          │  (con Price Snapshotting)    │
--   │  prodotto_sku          │          └──────────────────────────────┘
--   └────────────────────────┘
--
-- PATTERN:
--   1. Single Table Inheritance → prodotto.tipo discrimina SEMPLICE/COMPOSTO
--   2. Adjacency List           → prodotto.id_padre FK autoreferenziale
--   3. Price Snapshotting        → configurazione_dettaglio.prezzo_unitario_congelato
--
-- =============================================================================

-- Pulizia in ordine inverso rispetto alle FK (le tabelle dipendenti prima)
DROP TABLE IF EXISTS configurazione_dettaglio;
DROP TABLE IF EXISTS configurazione;
DROP TABLE IF EXISTS prodotto_sku;
DROP TABLE IF EXISTS sku;
DROP TABLE IF EXISTS prodotto;
DROP TABLE IF EXISTS utente;

-- =============================================================================
-- 0. UTENTE — Fondazione dell'Autenticazione
-- =============================================================================
-- Senza questa tabella non esiste login. Il flusso è:
--   1. Client manda username + password
--   2. Servlet chiama UtenteDAO.checkCredentials(username, hash(password))
--   3. Se match → session.setAttribute("utente", utentePojo)
--   4. Da quel momento, JSESSIONID identifica l'utente
--
-- PK naturale su username: in un sistema con poche centinaia di utenti
-- (progetto accademico), non serve un id surrogate. La stringa è stabile,
-- leggibile, e le FK che puntano qui (configurazione.cliente_username)
-- diventano auto-esplicative nelle query.
-- =============================================================================
CREATE TABLE utente (
    username      VARCHAR(50)     PRIMARY KEY,
    password_hash VARCHAR(255)    NOT NULL,
    nome          VARCHAR(100)    NOT NULL,
    cognome       VARCHAR(100)    NOT NULL,
    ruolo         ENUM('FORNITORE', 'CLIENTE') NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 1. PRODOTTO — Area Catalogo (Master Data)
-- =============================================================================
-- SINGLE TABLE INHERITANCE:
--   Un'unica tabella per SEMPLICE e COMPOSTO. Il campo `tipo` è il
--   discriminatore. I campi descrizione/prezzo_min/prezzo_max sono
--   significativi solo per COMPOSTO (NULL per SEMPLICE).
--   Alternativa scartata: due tabelle separate (prodotto_semplice,
--   prodotto_composto) → JOIN inutili, query duplicate, incubo nel DAO.
--
-- ADJACENCY LIST:
--   id_padre punta al record padre nella stessa tabella.
--   NULL = prodotto radice di primo livello (il "PC Desktop").
--   Con profondità max 4 e CTE ricorsive di MySQL 8+, basta e avanza.
--   Non serve Nested Set (fragile sugli INSERT) né Closure Table (overkill).
--
-- ON DELETE CASCADE su id_padre:
--   Cancello il padre → cancello tutta la sottogerarchia.
--   Il bottone "-*" del fornitore sfrutta esattamente questo comportamento.
-- =============================================================================
CREATE TABLE prodotto (
    id          INT             AUTO_INCREMENT PRIMARY KEY,
    codice      INT             NOT NULL UNIQUE,
    nome        VARCHAR(200)    NOT NULL,
    tipo        ENUM('SEMPLICE', 'COMPOSTO') NOT NULL,

    -- Campi esclusivi COMPOSTO (NULL per SEMPLICE)
    descrizione TEXT            NULL,
    prezzo_min  DECIMAL(10, 2)  NULL,
    prezzo_max  DECIMAL(10, 2)  NULL,

    -- Adjacency List
    id_padre    INT             NULL,

    CONSTRAINT chk_prezzo_min_pos CHECK (prezzo_min IS NULL OR prezzo_min >= 0),
    CONSTRAINT chk_prezzo_max_pos CHECK (prezzo_max IS NULL OR prezzo_max >= 0),
    CONSTRAINT chk_fascia_prezzo  CHECK (
        prezzo_min IS NULL OR prezzo_max IS NULL OR prezzo_max >= prezzo_min
    ),

    CONSTRAINT fk_prodotto_padre
        FOREIGN KEY (id_padre) REFERENCES prodotto(id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_prodotto_id_padre ON prodotto(id_padre);
CREATE INDEX idx_prodotto_nome     ON prodotto(nome);

-- =============================================================================
-- 2. SKU — Area Catalogo (Master Data)
-- =============================================================================
-- Realizzazione concreta di un prodotto semplice.
-- Es: prodotto "CPU" → SKU "AMD Ryzen 7 7700X", "Intel i7-14700K", ecc.
--
-- Il campo `prezzo` è il prezzo CORRENTE nel catalogo.
-- ATTENZIONE: questo prezzo è VOLATILE. Può cambiare in qualsiasi momento.
-- Per questo nelle transazioni congeleremo il prezzo (vedi tabella 5).
-- =============================================================================
CREATE TABLE sku (
    id                  INT             AUTO_INCREMENT PRIMARY KEY,
    codice              INT             NOT NULL UNIQUE,
    nome                VARCHAR(200)    NOT NULL,
    fotografia          VARCHAR(500)    NULL,
    descrizione_tecnica TEXT            NULL,
    prezzo              DECIMAL(10, 2)  NOT NULL,

    CONSTRAINT chk_sku_prezzo_pos CHECK (prezzo >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_sku_nome ON sku(nome);

-- =============================================================================
-- 3. PRODOTTO_SKU — Area Catalogo (Tabella di Giunzione N:M)
-- =============================================================================
-- Many-to-Many: un prodotto semplice ha molte SKU, una SKU puo' appartenere
-- a piu' prodotti semplici (polimorfismo SKU, da specifica).
--
-- PK composita: impedisce di associare la stessa SKU allo stesso prodotto
-- due volte. Non servono UNIQUE aggiuntivi.
--
-- CASCADE su entrambe le FK: coerente col dominio. Se cancello un prodotto,
-- le sue associazioni non hanno piu' senso. Idem per una SKU.
-- =============================================================================
CREATE TABLE prodotto_sku (
    id_prodotto INT NOT NULL,
    id_sku      INT NOT NULL,
    PRIMARY KEY (id_prodotto, id_sku),

    CONSTRAINT fk_ps_prodotto FOREIGN KEY (id_prodotto)
        REFERENCES prodotto(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_ps_sku FOREIGN KEY (id_sku)
        REFERENCES sku(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- =============================================================================
-- 4. CONFIGURAZIONE — Area Transazioni
-- =============================================================================
-- Rappresenta lo "scontrino" del cliente: una selezione di SKU per ogni
-- prodotto semplice dell'albero di un prodotto composto radice.
--
-- cliente_username: FK verso utente. Ci dice CHI ha creato la configurazione.
--   Il filtro di autorizzazione nel Servlet verifica che l'utente loggato
--   possa accedere SOLO alle proprie configurazioni.
--
-- prodotto_radice_id: FK verso prodotto. Ci dice PER QUALE prodotto composto
--   di primo livello è stata creata questa configurazione.
--   Senza questo campo, dovresti risalire dai dettagli → SKU → prodotto
--   per ricostruire l'albero di appartenenza. Un incubo.
--
-- data_modifica: auto-aggiornata da MySQL ad ogni UPDATE. Serve per la
--   pagina "Le mie configurazioni" (ordinamento per data modifica).
-- =============================================================================
CREATE TABLE configurazione (
    id                  INT             AUTO_INCREMENT PRIMARY KEY,
    cliente_username    VARCHAR(50)     NOT NULL,
    prodotto_radice_id  INT             NOT NULL,
    nome                VARCHAR(200)    NOT NULL,
    data_creazione      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    data_modifica       DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP
                                        ON UPDATE CURRENT_TIMESTAMP,
    prezzo_totale       DECIMAL(10, 2)  NOT NULL DEFAULT 0.00,

    CONSTRAINT chk_cfg_prezzo_pos CHECK (prezzo_totale >= 0),

    CONSTRAINT fk_cfg_cliente FOREIGN KEY (cliente_username)
        REFERENCES utente(username) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_cfg_prodotto_radice FOREIGN KEY (prodotto_radice_id)
        REFERENCES prodotto(id) ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_cfg_cliente ON configurazione(cliente_username);
CREATE INDEX idx_cfg_data    ON configurazione(data_creazione DESC);

-- =============================================================================
-- 5. CONFIGURAZIONE_DETTAGLIO — Area Transazioni (PRICE SNAPSHOTTING)
-- =============================================================================
-- Questa e' la tabella piu' importante dal punto di vista architetturale.
--
-- *** IL PROBLEMA ***
-- Il prezzo della SKU "AMD Ryzen 7" nel catalogo (tabella sku) oggi e' 329 EUR.
-- Il cliente Mario salva una configurazione con quella SKU.
-- Domani il fornitore alza il prezzo a 349 EUR.
-- Se NON congelo il prezzo, lo "scontrino" di Mario CAMBIA RETROATTIVAMENTE
-- da 329 a 349 EUR. Inaccettabile sia legalmente che per la UX.
--
-- *** LA SOLUZIONE: SNAPSHOTTING ***
-- Al momento del salvataggio, COPIO il prezzo corrente della SKU
-- nel campo `prezzo_unitario_congelato`. Da quel momento, il prezzo
-- nella configurazione e' IMMUTABILE. Il catalogo puo' cambiare quanto
-- vuole: lo scontrino del cliente resta fissato al momento della conferma.
--
-- id_prodotto: QUALE prodotto semplice questa riga rappresenta.
--   Senza questo campo, se due prodotti semplici condividono la stessa SKU
--   (polimorfismo da specifica), non sapresti a quale dei due si riferisce.
--
-- PK composita (id_configurazione, id_prodotto):
--   Una configurazione ha esattamente UNA scelta SKU per ogni prodotto
--   semplice. La PK su (configurazione, prodotto) lo garantisce.
-- =============================================================================
CREATE TABLE configurazione_dettaglio (
    id_configurazione         INT             NOT NULL,
    id_prodotto               INT             NOT NULL,
    id_sku                    INT             NOT NULL,
    prezzo_unitario_congelato DECIMAL(10, 2)  NOT NULL,

    PRIMARY KEY (id_configurazione, id_prodotto),

    CONSTRAINT fk_cd_configurazione FOREIGN KEY (id_configurazione)
        REFERENCES configurazione(id) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_cd_prodotto FOREIGN KEY (id_prodotto)
        REFERENCES prodotto(id) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_cd_sku FOREIGN KEY (id_sku)
        REFERENCES sku(id) ON DELETE RESTRICT ON UPDATE CASCADE,

    CONSTRAINT chk_cd_prezzo_pos CHECK (prezzo_unitario_congelato >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
