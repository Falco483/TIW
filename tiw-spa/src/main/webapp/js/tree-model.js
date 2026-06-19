/**
 * Tree Model — funzioni PURE di manipolazione del modello dell'albero del prodotto composto.
 *
 * Questo modulo NON conosce il DOM né la coda delle azioni pendenti: si limita a leggere
 * e modificare la struttura dati che rappresenta l'albero in costruzione
 * (`appFornitore.stato.currentTree`), che è l'unica fonte di verità (Single Source of Truth).
 *
 * Forma canonica dei nodi:
 *   Nodo COMPOSTO: { id, tipo:'COMPOSTO', codice, nome, prezzoMin, prezzoMax, figli: [Nodo] }
 *   Nodo SEMPLICE: { id, tipo:'SEMPLICE', codice, nome, skus: [Sku] }
 *   Sku:           { id, codice, nome, prezzo }
 *
 * Gli id possono essere reali (numerici, dal DB) o temporanei (stringhe "temp_...").
 * Tutti i confronti sono effettuati per valore stringa per essere robusti rispetto al tipo.
 */
const treeModel = {

    /**
     * Confronta due identificativi (di nodo o SKU) coercendoli a stringa.
     * @param {number|string} a
     * @param {number|string} b
     * @returns {boolean} true se rappresentano lo stesso id.
     */
    sameId: function (a, b) {
        return String(a) === String(b);
    },

    /**
     * Cerca ricorsivamente un nodo nell'albero a partire dal suo id.
     * @param {Object} tree - Il nodo radice da cui iniziare la ricerca.
     * @param {number|string} id - L'id del nodo da trovare.
     * @returns {Object|null} Il nodo trovato, o null se assente.
     */
    findNode: function (tree, id) {
        if (!tree) return null;
        if (this.sameId(tree.id, id)) return tree;
        if (Array.isArray(tree.figli)) {
            for (const figlio of tree.figli) {
                const trovato = this.findNode(figlio, id);
                if (trovato) return trovato;
            }
        }
        return null;
    },

    /**
     * Cerca il nodo padre del nodo identificato da `id`.
     * @param {Object} tree - Il nodo radice da cui iniziare la ricerca.
     * @param {number|string} id - L'id del nodo figlio di cui trovare il padre.
     * @returns {Object|null} Il nodo padre, o null se il nodo è la radice o non esiste.
     */
    findParent: function (tree, id) {
        if (!tree || !Array.isArray(tree.figli)) return null;
        for (const figlio of tree.figli) {
            if (this.sameId(figlio.id, id)) return tree;
            const trovato = this.findParent(figlio, id);
            if (trovato) return trovato;
        }
        return null;
    },

    /**
     * Aggiunge un nodo figlio al nodo composto identificato da `parentId`.
     * @param {Object} tree - Il nodo radice dell'albero.
     * @param {number|string} parentId - L'id del nodo padre (COMPOSTO).
     * @param {Object} childNode - Il nuovo nodo figlio da inserire.
     * @returns {boolean} true se l'inserimento è avvenuto, false se il padre non è stato trovato.
     */
    addChild: function (tree, parentId, childNode) {
        const padre = this.findNode(tree, parentId);
        if (!padre) return false;
        if (!Array.isArray(padre.figli)) padre.figli = [];
        padre.figli.push(childNode);
        return true;
    },

    /**
     * Rimuove un nodo dall'albero (scollegamento o eliminazione dal modello).
     * @param {Object} tree - Il nodo radice dell'albero.
     * @param {number|string} id - L'id del nodo da rimuovere.
     * @returns {{parentId: (number|string), index: number}|null} La posizione originale del nodo
     *          (id del padre e indice tra i fratelli) utile per un eventuale ripristino, oppure null
     *          se il nodo non è stato trovato o è la radice (non rimovibile).
     */
    removeNode: function (tree, id) {
        const padre = this.findParent(tree, id);
        if (!padre || !Array.isArray(padre.figli)) return null;
        const index = padre.figli.findIndex(f => this.sameId(f.id, id));
        if (index === -1) return null;
        padre.figli.splice(index, 1);
        return { parentId: padre.id, index: index };
    },

    /**
     * Aggiorna i campi (attributi) di un nodo esistente.
     * Vengono copiati nel nodo solo i campi presenti nell'oggetto `fields`.
     * @param {Object} tree - Il nodo radice dell'albero.
     * @param {number|string} id - L'id del nodo da aggiornare.
     * @param {Object} fields - Mappa campo→valore da applicare (es. { nome, codice, prezzoMin }).
     * @returns {boolean} true se il nodo è stato trovato e aggiornato.
     */
    updateNodeFields: function (tree, id, fields) {
        const nodo = this.findNode(tree, id);
        if (!nodo) return false;
        Object.assign(nodo, fields);
        return true;
    },

    /**
     * Associa una SKU a un nodo semplice.
     * @param {Object} tree - Il nodo radice dell'albero.
     * @param {number|string} simpleNodeId - L'id del nodo SEMPLICE.
     * @param {Object} sku - La SKU da associare ({ id, codice, nome, prezzo }).
     * @returns {boolean} true se l'associazione è avvenuta, false se il nodo non esiste o non è SEMPLICE.
     */
    addSku: function (tree, simpleNodeId, sku) {
        const nodo = this.findNode(tree, simpleNodeId);
        if (!nodo || nodo.tipo !== 'SEMPLICE') return false;
        if (!Array.isArray(nodo.skus)) nodo.skus = [];
        nodo.skus.push(sku);
        return true;
    },

    /**
     * Rimuove l'associazione tra un nodo semplice e una SKU.
     * @param {Object} tree - Il nodo radice dell'albero.
     * @param {number|string} simpleNodeId - L'id del nodo SEMPLICE.
     * @param {number|string} skuId - L'id della SKU da scollegare.
     * @returns {number|null} L'indice originale della SKU rimossa (per un eventuale ripristino),
     *          oppure null se non trovata.
     */
    removeSku: function (tree, simpleNodeId, skuId) {
        const nodo = this.findNode(tree, simpleNodeId);
        if (!nodo || !Array.isArray(nodo.skus)) return null;
        const index = nodo.skus.findIndex(s => this.sameId(s.id, skuId));
        if (index === -1) return null;
        nodo.skus.splice(index, 1);
        return index;
    },

    /**
     * Aggiorna i campi di una SKU associata a un nodo semplice.
     * @param {Object} tree - Il nodo radice dell'albero.
     * @param {number|string} simpleNodeId - L'id del nodo SEMPLICE che possiede la SKU.
     * @param {number|string} skuId - L'id della SKU da aggiornare.
     * @param {Object} fields - Mappa campo→valore da applicare (es. { nome, codice, prezzo }).
     * @returns {boolean} true se la SKU è stata trovata e aggiornata.
     */
    updateSkuFields: function (tree, simpleNodeId, skuId, fields) {
        const nodo = this.findNode(tree, simpleNodeId);
        if (!nodo || !Array.isArray(nodo.skus)) return false;
        const sku = nodo.skus.find(s => this.sameId(s.id, skuId));
        if (!sku) return false;
        Object.assign(sku, fields);
        return true;
    }
};
