-- =============================================================================
-- PROGETTO TIW — Configuratore di Prodotto
-- Script DML — Dati di test
-- =============================================================================
-- Prerequisito: eseguire schema.sql PRIMA di questo script.
--
-- Password in chiaro (per login durante i test):
--   mario    → pass1   (CLIENTE)
--   luigi    → pass2   (CLIENTE)
--   anna     → pass3   (CLIENTE)
--   paolo    → pass4   (FORNITORE)
--
-- Hash generati con BCrypt (jbcrypt 0.4, cost factor 10, prefisso $2a$).
-- =============================================================================

-- =============================================================================
-- 0. UTENTE
-- =============================================================================
INSERT INTO utente (username, password_hash, nome, cognome, ruolo) VALUES
('mario', '$2a$10$ITuCjHjk.SfAIEHEBtpHn.ydg4GSdSkoxQb9sEuwbjbTNhDAg82KK', 'Mario', 'Rossi',   'CLIENTE'),
('luigi', '$2a$10$xxbibfLEdPavt5kPUumikOA5cdpl9o/fQq.ywW1Sr80L0/cFTLbLC', 'Luigi', 'Bianchi', 'CLIENTE'),
('anna',  '$2a$10$Iita/yNH8ACt1Ricfz3wWu0J6nfbdEbZYYxCRuKPgUqmcMxP4Fo8y', 'Anna',  'Verdi',   'CLIENTE'),
('paolo', '$2a$10$G1c/xcsCqnn050IfvLl/GOMNo8AjLELjm7obHyIswfmE.j3I4PnBm', 'Paolo', 'Neri',    'FORNITORE');

-- =============================================================================
-- 1. PRODOTTO — Gerarchia: PC Desktop (profondità 2)
-- =============================================================================
-- Struttura dell'albero:
--
--   PC Desktop (COMPOSTO, radice, id=1)
--   ├── CPU          (SEMPLICE, id=2)
--   ├── RAM          (SEMPLICE, id=3)
--   ├── Disco        (SEMPLICE, id=4)
--   └── Scheda Video (SEMPLICE, id=5)
-- =============================================================================

-- Radice (COMPOSTO, id_padre = NULL)
INSERT INTO prodotto (codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre) VALUES
(1000, 'PC Desktop', 'COMPOSTO', 'Configurazione base per PC Desktop', 500.00, 3000.00, NULL);

-- Figli diretti della radice (SEMPLICE)
-- prezzo_min/prezzo_max calcolati come MIN/MAX dei prezzi degli SKU associati:
--   CPU:          min=150.00 (Ryzen 5 5600),   max=250.00 (Ryzen 7 5800X)
--   RAM:          min= 35.00 (8 GB DDR4),       max=110.00 (32 GB DDR4)
--   Disco:        min= 30.00 (SSD 256 GB),      max= 90.00 (SSD 1 TB)
--   Scheda Video: min=160.00 (GTX 1650),        max=550.00 (RTX 4070)
INSERT INTO prodotto (codice, nome, tipo, descrizione, prezzo_min, prezzo_max, id_padre) VALUES
(1001, 'CPU',          'SEMPLICE', NULL, 150.00, 250.00, 1),
(1002, 'RAM',          'SEMPLICE', NULL,  35.00, 110.00, 1),
(1003, 'Disco',        'SEMPLICE', NULL,  30.00,  90.00, 1),
(1004, 'Scheda Video', 'SEMPLICE', NULL, 160.00, 550.00, 1);

-- =============================================================================
-- 2. SKU — Varianti concrete per ogni prodotto semplice
-- =============================================================================
INSERT INTO sku (codice, nome, fotografia, descrizione_tecnica, prezzo) VALUES
-- CPU (3 SKU)
(1001, 'Ryzen 5 5600',   NULL, '6 core, 3.5 GHz',   150.00),
(1002, 'Ryzen 7 5800X',  NULL, '8 core, 3.8 GHz',   250.00),
(1003, 'Intel i5-12400', NULL, '6 core, 2.5 GHz',   180.00),

-- RAM (3 SKU)
(2001, '8 GB DDR4',      NULL, '3200 MHz, CL16',     35.00),
(2002, '16 GB DDR4',     NULL, '3200 MHz, CL16',     60.00),
(2003, '32 GB DDR4',     NULL, '3600 MHz, CL18',    110.00),

-- Disco (3 SKU)
(3001, 'SSD 256 GB',     NULL, 'SATA III, 500 MB/s', 30.00),
(3002, 'SSD 512 GB',     NULL, 'NVMe, 3500 MB/s',    55.00),
(3003, 'SSD 1 TB',       NULL, 'NVMe, 5000 MB/s',    90.00),

-- Scheda Video (3 SKU)
(4001, 'GTX 1650',       NULL, '4 GB GDDR6',        160.00),
(4002, 'RTX 3060',       NULL, '12 GB GDDR6',       300.00),
(4003, 'RTX 4070',       NULL, '12 GB GDDR6X',      550.00);

-- =============================================================================
-- 3. PRODOTTO_SKU — Associazione N:M tra prodotti semplici e SKU
-- =============================================================================
-- CPU (prodotto id=2) → SKU id 1,2,3
INSERT INTO prodotto_sku (id_prodotto, id_sku) VALUES
(2, 1), (2, 2), (2, 3);

-- RAM (prodotto id=3) → SKU id 4,5,6
INSERT INTO prodotto_sku (id_prodotto, id_sku) VALUES
(3, 4), (3, 5), (3, 6);

-- Disco (prodotto id=4) → SKU id 7,8,9
INSERT INTO prodotto_sku (id_prodotto, id_sku) VALUES
(4, 7), (4, 8), (4, 9);

-- Scheda Video (prodotto id=5) → SKU id 10,11,12
INSERT INTO prodotto_sku (id_prodotto, id_sku) VALUES
(5, 10), (5, 11), (5, 12);

-- =============================================================================
-- 4. CONFIGURAZIONE — Una sola configurazione di mario per il PC Desktop
-- =============================================================================
-- prezzo_totale = 250 + 60 + 55 + 300 = 665.00
INSERT INTO configurazione (cliente_username, prodotto_radice_id, nome, prezzo_totale) VALUES
('mario', 1, 'Il mio PC da gaming', 665.00);

-- =============================================================================
-- 5. CONFIGURAZIONE_DETTAGLIO — 4 righe collegate alla configurazione id=1
-- =============================================================================
-- Mario ha scelto:
--   CPU:    Ryzen 7 5800X  (SKU id=2,  prezzo 250.00)
--   RAM:    16 GB DDR4     (SKU id=5,  prezzo  60.00)
--   Disco:  SSD 512 GB     (SKU id=8,  prezzo  55.00)
--   GPU:    RTX 3060       (SKU id=11, prezzo 300.00)
INSERT INTO configurazione_dettaglio (id_configurazione, id_prodotto, id_sku, prezzo_unitario_congelato) VALUES
(1, 2,  2,  250.00),   -- CPU:    Ryzen 7 5800X
(1, 3,  5,   60.00),   -- RAM:    16 GB DDR4
(1, 4,  8,   55.00),   -- Disco:  SSD 512 GB
(1, 5, 11,  300.00);   -- GPU:    RTX 3060
