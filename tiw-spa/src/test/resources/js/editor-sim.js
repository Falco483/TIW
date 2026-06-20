/**
 * Editor Simulator — harness di test che riproduce l'orchestrazione undo/redo
 * dell'editor dell'albero (ciò che app-fornitore farà negli Step 3-4 del piano),
 * appoggiandosi ai moduli REALI `treeModel` e `history`.
 *
 * Mantiene lo stato pendente (currentTree, pendingActions, skuAssociate), applica le
 * operazioni con la strategia "snapshot-before" + no-op detection, ed espone query
 * per le asserzioni dei test JUnit.
 *
 * Usato esclusivamente dai test (caricato via GraalVM JS). NON fa parte del runtime.
 */
const editorSim = {

    stato: { currentTree: null, pendingActions: [], skuAssociate: [] },

    /** Clone profondo basato su JSON (iniettato in history al posto di structuredClone). */
    clone: function (v) { return v === null || v === undefined ? null : JSON.parse(JSON.stringify(v)); },

    /** Cattura uno snapshot dello stato pendente corrente (Memento). */
    snapshot: function () {
        return {
            tree: this.clone(this.stato.currentTree),
            pendingActions: this.clone(this.stato.pendingActions),
            skuAssociate: this.clone(this.stato.skuAssociate)
        };
    },

    /** Inizia una sessione di editing: stato iniziale + cronologia vuota (R14). */
    start: function (jsonTree) {
        this.stato.currentTree = jsonTree ? JSON.parse(jsonTree) : null;
        this.stato.pendingActions = [];
        this.stato.skuAssociate = [];
        history.init({ cloneFn: this.clone });
    },

    /**
     * Esegue un'operazione undoable: cattura lo stato precedente, esegue, e committa
     * SOLO se qualcosa è effettivamente cambiato (no-op detection — R3/EC3).
     * @returns {boolean} true se è stato registrato un checkpoint.
     */
    exec: function (fn) {
        var before = this.snapshot();
        fn();
        // No-op detection sul MODELLO (R3/EC3): se l'albero non è cambiato, l'operazione
        // è ininfluente — si annullano eventuali effetti collaterali e non si committa.
        var changed = JSON.stringify(before.tree) !== JSON.stringify(this.stato.currentTree);
        if (changed) {
            history.commit(before);
        } else {
            this.apply(before);
        }
        return changed;
    },

    /** Applica uno snapshot ripristinato (model = unica fonte di verità). */
    apply: function (s) {
        this.stato.currentTree = this.clone(s.tree);
        this.stato.pendingActions = this.clone(s.pendingActions);
        this.stato.skuAssociate = this.clone(s.skuAssociate);
    },

    undo: function () { var s = history.undo(this.snapshot()); if (s) { this.apply(s); return true; } return false; },
    redo: function () { var s = history.redo(this.snapshot()); if (s) { this.apply(s); return true; } return false; },
    canUndo: function () { return history.canUndo(); },
    canRedo: function () { return history.canRedo(); },

    // ---------------------------------------------------------------------
    // Operazioni che rispecchiano gli handler reali di app-fornitore
    // ---------------------------------------------------------------------

    /** A — Creazione del prodotto composto radice. */
    createRoot: function (jsonNode) {
        return this.exec(function () {
            var n = JSON.parse(jsonNode);
            editorSim.stato.currentTree = n;
            editorSim.stato.pendingActions.push({ action: 'CREATE_NODE', root: true, id: n.id, tipo: n.tipo });
        });
    },

    /** C — Creazione di un sottoprodotto. */
    addChild: function (parentId, jsonChild) {
        return this.exec(function () {
            var c = JSON.parse(jsonChild);
            treeModel.addChild(editorSim.stato.currentTree, parentId, c);
            editorSim.stato.pendingActions.push({ action: 'CREATE_NODE', tempId: c.id, parentId: parentId, tipo: c.tipo });
        });
    },

    /** D — Scelta/associazione di una SKU a un prodotto semplice. */
    addSku: function (parentId, jsonSku) {
        return this.exec(function () {
            var s = JSON.parse(jsonSku);
            treeModel.addSku(editorSim.stato.currentTree, parentId, s);
            editorSim.stato.skuAssociate.push(String(s.id));
            editorSim.stato.pendingActions.push({ action: 'ADD_SKU', parentId: parentId, skuId: s.id });
        });
    },

    /** B — Inserimento/modifica del valore di un attributo di un nodo. */
    editNode: function (id, field, value) {
        return this.exec(function () {
            var f = {}; f[field] = value;
            treeModel.updateNodeFields(editorSim.stato.currentTree, id, f);
            editorSim.stato.pendingActions.push({ action: 'UPDATE_NODE', id: id, field: field, value: value });
        });
    },

    /** B con validazione — EC2: un valore vuoto è rifiutato e non genera checkpoint. */
    editNodeValidato: function (id, field, value) {
        if (value === '') return false;
        return this.editNode(id, field, value);
    },

    /** E1 — Cancellazione di un'associazione prodotto→prodotto. */
    unlinkNode: function (id) {
        return this.exec(function () {
            treeModel.removeNode(editorSim.stato.currentTree, id);
            editorSim.stato.pendingActions.push({ action: 'UNLINK_NODE', id: id });
        });
    },

    /** E2 — Cancellazione di un'associazione prodotto→SKU. */
    unlinkSku: function (parentId, skuId) {
        return this.exec(function () {
            treeModel.removeSku(editorSim.stato.currentTree, parentId, skuId);
            editorSim.stato.skuAssociate = editorSim.stato.skuAssociate.filter(function (x) { return x !== String(skuId); });
            editorSim.stato.pendingActions.push({ action: 'UNLINK_SKU', parentId: parentId, skuId: skuId });
        });
    },

    // ---------------------------------------------------------------------
    // Query per le asserzioni dei test
    // ---------------------------------------------------------------------

    treeJSON: function () { return JSON.stringify(this.stato.currentTree); },
    pendingCount: function () { return this.stato.pendingActions.length; },
    skuAssociateCount: function () { return this.stato.skuAssociate.length; },
    hasNode: function (id) { return treeModel.findNode(this.stato.currentTree, id) !== null; },
    childCount: function (id) { var n = treeModel.findNode(this.stato.currentTree, id); return n && n.figli ? n.figli.length : -1; },
    skuCount: function (id) { var n = treeModel.findNode(this.stato.currentTree, id); return n && n.skus ? n.skus.length : -1; },
    nodeField: function (id, field) { var n = treeModel.findNode(this.stato.currentTree, id); return n ? String(n[field]) : null; },
    hasSku: function (parentId, skuId) {
        var n = treeModel.findNode(this.stato.currentTree, parentId);
        if (!n || !n.skus) return false;
        return n.skus.some(function (s) { return String(s.id) === String(skuId); });
    },
    childIndexOf: function (parentId, childId) {
        var n = treeModel.findNode(this.stato.currentTree, parentId);
        if (!n || !n.figli) return -1;
        for (var i = 0; i < n.figli.length; i++) {
            if (String(n.figli[i].id) === String(childId)) return i;
        }
        return -1;
    }
};
globalThis.editorSim = editorSim;
