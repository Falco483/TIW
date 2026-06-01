/**
 * Network Layer per la SPA del Fornitore.
 * Contiene esclusivamente le funzioni asincrone per comunicare con il backend via fetch.
 */
const api = {
    
    /**
     * Funzione helper per ottenere il token CSRF dal meta tag.
     * @returns {string} Il token CSRF estratto, o una stringa vuota se non trovato.
     */
    getCsrfToken: function() {
        const meta = document.querySelector('meta[name="_csrf"]');
        return meta ? meta.getAttribute('content') : '';
    },

    /**
     * Funzione helper per gestire le fetch e gli errori comuni.
     * @param {string} url - L'URL relativo o assoluto da chiamare.
     * @param {Object} [options={}] - Le opzioni per la chiamata fetch (metodo, headers, body, ecc.).
     * @returns {Promise<any>} Una Promise che si risolve con i dati JSON della risposta o true se 204 NoContent.
     * @throws {Error} Se lo status HTTP è di errore o se la sessione è scaduta (401).
     */
    fetchJson: async function(url, options = {}) {
        if (!options.headers) {
            options.headers = {};
        }
        
        // Aggiungi CSRF token per le richieste mutanti
        if (options.method && options.method !== 'GET' && options.method !== 'HEAD') {
            options.headers['X-CSRF-Token'] = this.getCsrfToken();
            if (!options.headers['Content-Type'] && !(options.body instanceof FormData)) {
                options.headers['Content-Type'] = 'application/json';
            }
        }

        const response = await fetch(url, options);

        // Se 401 Unauthorized, redirigi al login (la sessione è scaduta)
        if (response.status === 401) {
            window.location.href = 'login.html';
            throw new Error('Sessione scaduta');
        }

        // Se la risposta non ha corpo o è NoContent, ritorna true
        if (response.status === 204) return true;

        const data = await response.json().catch(() => ({ error: 'Risposta del server non valida' }));

        if (!response.ok) {
            const msg = data.error || data.errore || `Errore HTTP ${response.status}`;
            throw new Error(msg);
        }

        return data;
    },

    /**
     * Recupera le info dell'utente corrente (nome, cognome, ecc.) dal backend.
     * @returns {Promise<Object>} Una Promise con i dati dell'utente.
     */
    getUser: function() {
        return this.fetchJson('api/me');
    },

    /**
     * Recupera tutte le SKU dal catalogo.
     * @returns {Promise<Object>} Una Promise con il payload contenente l'elenco delle SKU.
     */
    getAllSkus: function() {
        return this.fetchJson('api/sku');
    },

    /**
     * Crea una nuova SKU inviando i dati multipart/form-data.
     * @param {FormData} skuFormData - I dati del form contenenti le informazioni della SKU e l'eventuale file immagine.
     * @returns {Promise<Object>} Una Promise con la conferma della creazione e i dati salvati.
     */
    createSku: function(skuFormData) {
        return this.fetchJson('api/sku', {
            method: 'POST',
            body: skuFormData
        });
    },

    /**
     * Aggiorna una SKU esistente.
     * @param {FormData} skuFormData - I dati aggiornati del form.
     * @returns {Promise<Object>} Una Promise con l'esito dell'aggiornamento.
     */
    updateSku: function(skuFormData) {
        return this.fetchJson('api/sku', {
            method: 'PUT',
            body: skuFormData
        });
    },

    /**
     * Elimina definitivamente una SKU (se non in uso da alcun prodotto).
     * @param {number|string} id - L'identificativo univoco (ID surrogato) della SKU da eliminare.
     * @returns {Promise<Object>} Una Promise con l'esito dell'eliminazione.
     */
    deleteSku: function(id) {
        return this.fetchJson(`api/sku?id=${id}`, {
            method: 'DELETE'
        });
    },

    /**
     * Recupera l'albero completo di un prodotto dato il suo ID.
     * @param {number|string} id - L'ID del prodotto radice.
     * @returns {Promise<Object>} Una Promise con l'oggetto Prodotto contenente eventuali figli annidati.
     */
    getTree: function(id) {
        return this.fetchJson(`api/prodotto?id=${id}`);
    },

    /**
     * Recupera la lista di tutti i prodotti radice (che non hanno padri).
     * @returns {Promise<Object>} Una Promise con l'elenco dei prodotti di livello zero.
     */
    getProdottiRadice: function() {
        return this.fetchJson('api/prodotto');
    },

    /**
     * Salva l'intero albero in costruzione come nuova entità transazionale.
     * @param {Object} treeJSON - L'oggetto JSON che modella il nuovo albero.
     * @returns {Promise<Object>} Una Promise con l'esito del salvataggio.
     */
    saveTree: function(treeJSON) {
        return this.fetchJson('api/prodotto', {
            method: 'POST',
            body: JSON.stringify(treeJSON)
        });
    },

    /**
     * Sincronizza una coda di azioni sull'albero (usato per il Tree Editor interattivo).
     * @param {Array<Object>} actionsArray - Array di azioni da processare in batch sul server.
     * @returns {Promise<Object>} Una Promise con l'esito della sincronizzazione e l'eventuale albero aggiornato.
     */
    syncTree: function(actionsArray) {
        return this.fetchJson('api/sync', {
            method: 'POST',
            body: JSON.stringify(actionsArray)
        });
    },

    /**
     * Aggiorna i campi base di un prodotto esistente (es. nome o codice, non la struttura dell'albero).
     * @param {Object} prodottoData - I nuovi dati in formato JSON.
     * @returns {Promise<Object>} Una Promise con la conferma dell'aggiornamento.
     */
    updateProdotto: function(prodottoData) {
        return this.fetchJson('api/prodotto', {
            method: 'PUT',
            body: JSON.stringify(prodottoData)
        });
    },

    /**
     * Elimina definitivamente un prodotto dal database.
     * @param {number|string} id - L'ID del prodotto da eliminare.
     * @returns {Promise<Object>} Una Promise con l'esito dell'eliminazione.
     */
    deleteProdotto: function(id) {
        return this.fetchJson(`api/prodotto?id=${id}&azione=elimina`, {
            method: 'DELETE'
        });
    },

    /**
     * Scollega un nodo figlio dal suo padre (imposta id_padre a null nel database).
     * @param {number|string} id - L'ID del prodotto figlio da scollegare.
     * @returns {Promise<Object>} Una Promise con la conferma del disaccoppiamento.
     */
    scollegaProdotto: function(id) {
        return this.fetchJson(`api/prodotto?id=${id}&azione=scollega`, {
            method: 'DELETE'
        });
    },

    /**
     * Rimuove l'associazione N:M tra un prodotto semplice e una SKU.
     * @param {number|string} idProdotto - L'ID del prodotto semplice.
     * @param {number|string} idSku - L'ID della SKU da rimuovere.
     * @returns {Promise<Object>} Una Promise con la conferma della dissociazione.
     */
    scollegaSkuDaSemplice: function(idProdotto, idSku) {
        return this.fetchJson(`api/prodotto?id=${idProdotto}&azione=scollegaSku&idSku=${idSku}`, {
            method: 'DELETE'
        });
    },

    /**
     * Effettua la ricerca full-text su SKU e Prodotti.
     * @param {string} query - La stringa di ricerca inserita dall'utente.
     * @returns {Promise<Object>} Una Promise contenente i risultati testuali provenienti da SKU e prodotti.
     */
    search: function(query) {
        return this.fetchJson(`api/ricerca?q=${encodeURIComponent(query)}`);
    },

    // -------------------------------------------------------------------------
    // Cliente
    // -------------------------------------------------------------------------

    getConfigurazioni: function() {
        return this.fetchJson('api/cliente/configurazioni');
    },

    getConfigurazioneById: function(id) {
        return this.fetchJson(`api/cliente/configurazioni/${id}`);
    },

    getAlberoPerConfigurazione: function(codice) {
        return this.fetchJson(`api/cliente/configurazioni?codice=${codice}`);
    },

    salvaConfigurazione: function(payload) {
        return this.fetchJson('api/cliente/configurazioni', {
            method: 'POST',
            body: JSON.stringify(payload)
        });
    },

    aggiornaConfigurazione: function(id, payload) {
        return this.fetchJson(`api/cliente/configurazioni/${id}`, {
            method: 'PUT',
            body: JSON.stringify(payload)
        });
    },

    eliminaConfigurazione: function(id) {
        return this.fetchJson(`api/cliente/configurazioni/${id}`, {
            method: 'DELETE'
        });
    }
};