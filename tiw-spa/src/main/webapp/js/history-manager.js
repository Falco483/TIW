/**
 * History Manager — motore di Undo/Redo per l'editor dell'albero del prodotto composto.
 *
 * Design pattern:
 *   - Memento: ogni "snapshot" è un'istantanea immutabile (clone profondo) dello stato
 *     pendente della sessione di editing (albero, coda azioni, SKU associate).
 *   - Caretaker: questo modulo custodisce le due pile (past/future) senza conoscere
 *     la struttura interna degli snapshot.
 *
 * Il modulo è volutamente DISACCOPPIATO dalla UI e dal DOM: riceve e restituisce
 * snapshot opachi. La cattura dello snapshot corrente e la sua applicazione sono
 * responsabilità del chiamante (app-fornitore), che inietta `cloneFn` in fase di init.
 *
 * Strategia "snapshot-before": il chiamante registra (commit) lo stato ESISTENTE PRIMA
 * di eseguire un'operazione. Un undo ripristina quindi semplicemente lo snapshot
 * precedente, senza dover invertire le singole azioni.
 */
const history = {

    /** Pila degli stati precedenti (passato), dal più vecchio in fondo al più recente in cima. */
    _past: [],

    /** Pila degli stati annullati ri-ripristinabili (futuro). */
    _future: [],

    /**
     * Funzione di clonazione profonda usata per garantire l'immutabilità degli snapshot.
     * Iniettabile per testabilità; di default usa structuredClone.
     * @type {(value: any) => any}
     */
    _clone: (value) => structuredClone(value),

    /**
     * Inizializza (o reinizializza) la cronologia svuotando entrambe le pile.
     * Da chiamare all'apertura di una sessione di editing (R14 delle specifiche).
     * @param {{cloneFn?: (value:any)=>any}} [options] - Opzioni di configurazione.
     *        cloneFn: funzione di deep-clone alternativa (utile nei test).
     */
    init: function (options = {}) {
        this._past = [];
        this._future = [];
        if (typeof options.cloneFn === 'function') {
            this._clone = options.cloneFn;
        }
    },

    /**
     * Registra un checkpoint: lo stato fornito viene spinto sulla pila del passato
     * e la pila del futuro viene svuotata (una nuova operazione invalida i redo — R2/R13).
     * @param {Object} snapshotPrima - Lo snapshot dello stato ESISTENTE prima dell'operazione.
     */
    commit: function (snapshotPrima) {
        this._past.push(this._clone(snapshotPrima));
        this._future = [];
    },

    /**
     * Annulla l'ultima operazione.
     * Sposta lo snapshot corrente sulla pila del futuro e restituisce lo snapshot
     * precedente da applicare. Non ha effetto se non c'è nulla da annullare.
     * @param {Object} snapshotCorrente - Lo stato attuale, salvato per consentire il redo.
     * @returns {Object|null} Lo snapshot da ripristinare, oppure null se la pila è vuota (R6).
     */
    undo: function (snapshotCorrente) {
        if (!this.canUndo()) return null;
        this._future.push(this._clone(snapshotCorrente));
        return this._past.pop();
    },

    /**
     * Ripristina l'ultima operazione annullata.
     * Sposta lo snapshot corrente sulla pila del passato e restituisce lo snapshot
     * da ri-applicare. Non ha effetto se non c'è nulla da ripristinare.
     * @param {Object} snapshotCorrente - Lo stato attuale, salvato per consentire un nuovo undo.
     * @returns {Object|null} Lo snapshot da ripristinare, oppure null se la pila è vuota (R9).
     */
    redo: function (snapshotCorrente) {
        if (!this.canRedo()) return null;
        this._past.push(this._clone(snapshotCorrente));
        return this._future.pop();
    },

    /**
     * Indica se è possibile effettuare un'operazione di undo.
     * @returns {boolean} true se la pila del passato non è vuota.
     */
    canUndo: function () {
        return this._past.length > 0;
    },

    /**
     * Indica se è possibile effettuare un'operazione di redo.
     * @returns {boolean} true se la pila del futuro non è vuota.
     */
    canRedo: function () {
        return this._future.length > 0;
    },

    /**
     * Azzera completamente la cronologia (es. dopo un salvataggio riuscito — R15,
     * o al cambio di prodotto — R17). Non modifica la funzione di clonazione configurata.
     */
    clear: function () {
        this._past = [];
        this._future = [];
    }
};
