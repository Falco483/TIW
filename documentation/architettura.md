# Schema Architetturale - TIW Configuratore di Prodotto

Questo documento descrive in modo schematico l'architettura tecnica dell'applicazione TIW Configuratore di Prodotto.

## 1. Struttura del Progetto (Maven Multi-Module)

Il progetto segue un'architettura modulare suddivisa in core condiviso e due layer di presentazione distinti (Server-Side Rendering e Single Page Application).

```mermaid
graph TD
    Parent[tiw-parent<br/>POM Padre] --> Core[tiw-core<br/>JAR Condiviso]
    Parent --> SSR[tiw-ssr<br/>WAR - HTML/Thymeleaf]
    Parent --> SPA[tiw-spa<br/>WAR - JSON/API]
    
    Core -.->|Dipendenza| SSR
    Core -.->|Dipendenza| SPA
    
    subgraph tiw-core
        Model[POJO]
        DAO[Data Access Objects]
        Filters[Filtri Sicurezza]
    end
    
    subgraph tiw-ssr
        WebControllers[Servlet Controllers]
        Thymeleaf[Template HTML]
    end
    
    subgraph tiw-spa
        APIControllers[API REST-Like]
        JSON[Risposte JSON]
    end
```

## 2. Architettura MVC e Flusso dei Dati

L'applicazione segue rigorosamente il pattern MVC tramite Servlet, garantendo una forte separazione tra logica di business, accesso ai dati e presentazione.

```mermaid
sequenceDiagram
    participant B as Browser/Client
    participant F as Filtri (CSRF, Access, Role)
    participant C as Servlet (Controller)
    participant D as DAO (Model)
    participant DB as Database MySQL
    participant V as View (Thymeleaf/JSON)

    B->>F: Richiesta HTTP
    F->>F: Validazione Sicurezza
    F->>C: Passaggio Richiesta
    C->>C: Validazione Input
    C->>D: Chiamata Metodi Logica
    D->>DB: Query SQL
    DB-->>D: ResultSet
    D-->>C: Dati POJO
    C->>V: Passaggio Model / Serializzazione
    V-->>B: Risposta (HTML o JSON)
```

## 3. Schema del Database

Il database è diviso concettualmente in due aree: Master Data (Catalogo) e Dati Transazionali (Configurazioni). È implementato usando la Single Table Inheritance per i prodotti e l'Adjacency List per la gerarchia, oltre a snapshot del prezzo per i dettagli della configurazione.

```mermaid
erDiagram
    utente {
        varchar(50) username PK
        varchar(255) password_hash
        varchar(100) nome
        varchar(100) cognome
        enum ruolo "FORNITORE, CLIENTE"
    }

    prodotto {
        int id PK
        int codice UK
        varchar(200) nome
        enum tipo "SEMPLICE, COMPOSTO"
        text descrizione
        decimal prezzo_min
        decimal prezzo_max
        int id_padre FK "Adjacency List"
    }

    sku {
        int id PK
        int codice UK
        varchar(200) nome
        varchar(500) fotografia
        text descrizione_tecnica
        decimal prezzo
    }

    prodotto_sku {
        int id_prodotto PK, FK
        int id_sku PK, FK
    }

    configurazione {
        int id PK
        varchar(50) cliente_username FK
        int prodotto_radice_id FK
        varchar(200) nome
        datetime data_creazione
        datetime data_modifica
        decimal prezzo_totale
    }

    configurazione_dettaglio {
        int id_configurazione PK, FK
        int id_prodotto PK, FK
        int id_sku FK
        decimal prezzo_unitario_congelato "Snapshot Prezzo"
    }

    utente ||--o{ configurazione : "crea"
    prodotto ||--o{ prodotto : "padre_figlio"
    prodotto ||--o{ prodotto_sku : "associa"
    sku ||--o{ prodotto_sku : "appartiene"
    configurazione ||--o{ configurazione_dettaglio : "contiene"
    configurazione_dettaglio }o--|| prodotto : "riferisce"
    configurazione_dettaglio }o--|| sku : "congela"
```

## 4. Stack Tecnologico

- **Database:** MySQL 8+ / MariaDB 10.5+
- **Backend:** Java 17, Jakarta Servlet API 6.0
- **Server:** Apache Tomcat 10.1
- **Presentazione (SSR):** Thymeleaf 3.1.2
- **Dati JSON (SPA):** Jackson 2.17.0
- **Sicurezza:** Filtri Custom (AccessControl, RoleControl, CSRF) + BCrypt (jbcrypt) per l'hashing password.
- **Build Tool:** Maven (Multi-Module)
