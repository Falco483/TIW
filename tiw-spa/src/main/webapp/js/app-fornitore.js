/**
 * AppFornitore - Orchestratore SPA per l'interfaccia Fornitore.
 * Gestisce lo stato in RAM, il routing tra sezioni e il rendering del DOM.
 */
const AppFornitore = {

    stato: {
        skusDisponibili: [],
        orfaniDisponibili: [],
        sezioneAttiva: 'home',
        skuAssociateProdottoCorrente: []  // Lista ID delle SKU associate al prodotto correntemente visualizzato
    },

    /**
     * Inizializza l'applicazione: aggancia gli eventi globali e recupera i dati iniziali dal server 
     * (utente, SKU disponibili, prodotti orfani).
     * Popola le checkbox e imposta il token CSRF.
     */
    init: async function() {
        this.bindGlobalEvents();
        
        try {
            const [userRes, skuRes, orfaniRes] = await Promise.all([
                api.getUser().catch(() => ({ username: 'Utente' })),
                api.getAllSkus().catch(() => ({ data: [] })),
                api.fetchJson('api/prodotto?orfani=true').catch(() => ({ data: [] }))
            ]);
            
            if (userRes && userRes.csrfToken) {
                const meta = document.querySelector('meta[name="_csrf"]');
                if (meta) meta.setAttribute('content', userRes.csrfToken);
            }
            
            const nomeStr = userRes.nome ? `${userRes.nome} ${userRes.cognome}` : userRes.username;
            document.getElementById('topUserName').textContent = nomeStr;

            if (skuRes.data) {
                this.stato.skusDisponibili = skuRes.data;
                this.renderSkuCheckboxList();
            }

            if (orfaniRes.data) {
                this.stato.orfaniDisponibili = orfaniRes.data;
                this.renderOrfaniCheckboxList();
            }
        } catch (err) {
            this.mostraMessaggio("Errore caricamento dati iniziali.", "error");
        }
    },

    /**
     * Associa tutti i listener per gli eventi globali (click, submit) agli elementi del DOM.
     * Gestisce la "event delegation" sui contenitori dinamici e la top navbar.
     */
    bindGlobalEvents: function() {
        const btnNavToggle = document.getElementById('btn-nav-toggle');
        if (btnNavToggle) {
            btnNavToggle.addEventListener('click', () => {
                if (this.stato.sezioneAttiva === 'home') {
                    this.switchSection('ricerca');
                    btnNavToggle.innerHTML = '<i class="fa-solid fa-house"></i> Home';
                } else {
                    this.switchSection('home');
                    btnNavToggle.innerHTML = 'Ricerca prodotto';
                }
            });
        }

        document.getElementById('form-sku').addEventListener('submit', (e) => this.handleSubmitSku(e));
        document.getElementById('form-semplice').addEventListener('submit', (e) => this.handleSubmitSemplice(e));
        document.getElementById('form-composto').addEventListener('submit', (e) => this.handleCreaComposto(e));

        document.getElementById('dettaglio-content').addEventListener('click', (e) => {
            if (e.target.closest('.btn-elimina-sku')) {
                const skuId = e.target.closest('.sku-created-card').dataset.skuId;
                this.handleEliminaSku(skuId);
            } else if (e.target.closest('.btn-elimina-prodotto')) {
                const prodId = e.target.closest('.prodotto-created-card').dataset.prodId;
                this.deleteProdottoFromDB(prodId);
            } else if (e.target.closest('.btn-unlink')) {
                this.handleTreeUnlink(e.target.closest('.tree-node'));
            } else if (e.target.closest('.btn-delete-node')) {
                this.handleTreeDelete(e.target.closest('.tree-node'));
            } else if (e.target.closest('.btn-unlink-sku')) {
                this.handleTreeUnlinkSku(e.target.closest('.tree-node'));
            } else if (e.target.closest('.btn-delete-sku')) {
                this.handleTreeDeleteSku(e.target.closest('.tree-node'));
            } else if (e.target.closest('.btn-add-child')) {
                this.handleTreeAddChild(e.target.closest('.tree-node'));
            } else if (e.target.closest('.btn-add-sku')) {
                this.handleTreeAddSku(e.target.closest('.tree-node'));
            }
        });

        // Inline edit for Tree Editor and SKU forms
        document.getElementById('dettaglio-content').addEventListener('focusout', (e) => {
            if (e.target.classList.contains('sku-inline-edit')) {
                this.handleSkuInlineEdit(e.target);
            } else if (e.target.hasAttribute('contenteditable')) {
                this.handleTreeInlineEdit(e.target);
            }
        });

        document.getElementById('btn-salva-tree').addEventListener('click', () => this.handleSalvaTree());

        document.getElementById('form-add-child').addEventListener('submit', (e) => this.submitTreeAddChild(e));
        document.getElementById('form-add-sku').addEventListener('submit', (e) => this.submitTreeAddSku(e));

        document.getElementById('form-ricerca').addEventListener('submit', (e) => this.handleSearch(e));
        document.getElementById('risultati-container').addEventListener('click', (e) => {
            const resultItem = e.target.closest('.search-result-item');
            if (resultItem && !resultItem.classList.contains('expanded')) {
                const id = resultItem.dataset.id;
                const tipo = resultItem.dataset.tipo;
                this.handleExpandSearchResult(resultItem, id, tipo);
            }
        });
    },

    /**
     * Cambia la sezione attiva dell'interfaccia (es. da "home" a "ricerca"), aggiornando la visualizzazione.
     * @param {string} sectionId - L'ID della sezione da attivare.
     */
    switchSection: function(sectionId) {
        document.querySelectorAll('.app-section').forEach(sec => sec.classList.remove('active'));
        document.getElementById(`section-${sectionId}`).classList.add('active');
        this.stato.sezioneAttiva = sectionId;
    },

    /**
     * Renderizza la lista delle checkbox per le SKU disponibili aggiornando il DOM.
     * Mostra un messaggio di "empty state" se non ci sono SKU disponibili.
     */
    renderSkuCheckboxList: function() {
        const container = document.getElementById('lista-skus-checkbox');
        container.innerHTML = '';
        if (this.stato.skusDisponibili.length === 0) {
            container.innerHTML = '<div class="empty-state" style="padding: 1rem;"><p>Nessuna SKU disponibile.</p></div>';
            return;
        }
        this.stato.skusDisponibili.forEach(sku => {
            const div = document.createElement('div');
            div.className = 'checkbox-item';
            div.innerHTML = `
                <input type="checkbox" name="skus_selezionate" value="${sku.id}" id="chk_sku_${sku.id}">
                <label for="chk_sku_${sku.id}">${this.escapeHtml(sku.nome)} (ID: ${sku.id}) - €${sku.prezzo}</label>
            `;
            container.appendChild(div);
        });
    },

    /**
     * Renderizza la lista delle checkbox per i prodotti orfani (senza padre) disponibili.
     * Mostra un messaggio se non ci sono prodotti orfani.
     */
    renderOrfaniCheckboxList: function() {
        const container = document.getElementById('lista-orfani-checkbox');
        container.innerHTML = '';
        if (this.stato.orfaniDisponibili.length === 0) {
            container.innerHTML = '<div class="empty-state" style="padding: 1rem;"><p>Nessun prodotto orfano disponibile.</p></div>';
            return;
        }
        this.stato.orfaniDisponibili.forEach(p => {
            const pMin = p.prezzoMin ?? 0;
            const pMax = p.prezzoMax ?? 0;
            const div = document.createElement('div');
            div.className = 'checkbox-item';
            div.innerHTML = `
                <input type="checkbox" name="orfani_selezionati" value="${p.id}" id="chk_orf_${p.id}"
                       data-prezzo-min="${pMin}" data-prezzo-max="${pMax}">
                <label for="chk_orf_${p.id}">
                    ${this.escapeHtml(p.nome)} (Cod: ${p.codice}) [${p.tipo}]
                    — €${parseFloat(pMin).toFixed(2)} / €${parseFloat(pMax).toFixed(2)}
                </label>
            `;
            container.appendChild(div);
        });

        if (!container.dataset.listenerAttached) {
            container.addEventListener('change', () => this.ricalcolaRiepilogoPrezzi());
            container.dataset.listenerAttached = 'true';
        }
    },

    ricalcolaRiepilogoPrezzi: function() {
        const checkboxes = document.querySelectorAll('#lista-orfani-checkbox input[name="orfani_selezionati"]:checked');
        let sumMin = 0;
        let sumMax = 0;
        checkboxes.forEach(chk => {
            sumMin += parseFloat(chk.dataset.prezzoMin || 0);
            sumMax += parseFloat(chk.dataset.prezzoMax || 0);
        });

        document.getElementById('sum-prezzo-min').textContent = sumMin.toFixed(2);
        document.getElementById('sum-prezzo-max').textContent = sumMax.toFixed(2);

        const riepilogo = document.getElementById('prezzo-riepilogo');
        riepilogo.style.display = checkboxes.length > 0 ? 'block' : 'none';

        const inputMin = document.querySelector('#form-composto input[name="prezzo_min"]');
        if (inputMin) inputMin.min = sumMin.toFixed(2);
    },

    /**
     * Gestisce l'evento di submit del form per la creazione di una nuova SKU.
     * Invia i dati tramite l'API e aggiorna l'interfaccia in caso di successo.
     * @param {Event} e - L'evento originato dal submit del form.
     */
    handleSubmitSku: async function(e) {
        e.preventDefault();
        const form = e.target;
        const submitBtn = form.querySelector('button[type="submit"]');
        submitBtn.disabled = true;

        const formData = new FormData(form);
        formData.append('descrizioneTecnica', formData.get('descrizione_tecnica') || '');

        try {
            const res = await api.createSku(formData);
            if (res.data) {
                this.mostraMessaggio("SKU creata con successo", "success");
                form.reset();
                this.stato.skusDisponibili.push(res.data);
                this.renderSkuCheckboxList();
                this.renderSkuCreata(res.data);
            }
        } catch (err) {
            this.mostraMessaggio(err.message, "error");
        } finally {
            submitBtn.disabled = false;
        }
    },

    /**
     * Assicura che il contenitore dei dettagli di creazione (`dettaglio-creazione`) 
     * sia posizionato all'interno della sezione attualmente attiva.
     */
    assureDetailContainerLocation: function() {
        const activeSection = document.getElementById(`section-${this.stato.sezioneAttiva}`);
        const det = document.getElementById('dettaglio-creazione');
        if (activeSection && det && det.parentNode !== activeSection) {
            activeSection.appendChild(det);
        }
    },

    /**
     * Renderizza i dettagli di una SKU appena creata utilizzando il template HTML nascosto.
     * @param {Object} sku - L'oggetto che rappresenta la SKU.
     */
    renderSkuCreata: function(sku) {
        this.assureDetailContainerLocation();
        const detailContainer = document.getElementById('dettaglio-creazione');
        const container = document.getElementById('dettaglio-content');
        detailContainer.style.display = 'block';

        const tpl = document.getElementById('tpl-sku-display').content.cloneNode(true);
        const card = tpl.querySelector('.sku-created-card');
        card.dataset.skuId = sku.id;
        
        tpl.querySelector('.sku-id-val').textContent = sku.id;
        tpl.querySelector('.sku-view-codice').textContent = sku.codice;
        tpl.querySelector('.sku-view-nome').textContent = sku.nome;
        tpl.querySelector('.sku-view-prezzo').textContent = sku.prezzo;
        tpl.querySelector('.sku-view-desc').textContent = sku.descrizioneTecnica || '-';
        
        const img = tpl.querySelector('.sku-view-foto');
        if (sku.fotografia) {
            img.src = sku.fotografia.startsWith('uploads/') ? sku.fotografia : 'uploads/' + sku.fotografia;
            img.style.display = 'block';
        }

        container.innerHTML = '';
        container.appendChild(tpl);
    },

    /**
     * Renderizza l'albero di un prodotto appena creato o caricato dal server.
     * Ripopola la lista delle SKU associate e inizializza l'editor dell'albero.
     * @param {Object} prodotto - I dati del prodotto radice da visualizzare.
     */
    renderProdottoCreato: async function(prodotto) {
        this.assureDetailContainerLocation();
        this.stato.pendingActions = [];
        // Svuota la lista SKU associate: sarà ripopolata durante il build del tree
        this.stato.skuAssociateProdottoCorrente = [];
        const detailContainer = document.getElementById('dettaglio-creazione');
        detailContainer.style.display = 'block';
        
        try {
            const res = await api.getTree(prodotto.id);
            if(res.data) {
                this.stato.currentTree = res.data;
                // Raccoglie gli ID delle SKU presenti nell'albero prima del render
                this.raccogliSkuDaAlbero(res.data);
                this.renderTreeView(res.data);
            }
        } catch(e) {
            this.mostraMessaggio("Errore caricamento albero: " + e.message, "error");
        }
    },

    /**
     * Attraversa ricorsivamente l'albero e raccoglie tutti gli ID delle SKU
     * nella lista locale skuAssociateProdottoCorrente.
     * @param {Object} node - Il nodo corrente dell'albero da ispezionare.
     */
    raccogliSkuDaAlbero: function(node) {
        if (node.tipo === 'SEMPLICE' && node.skus) {
            node.skus.forEach(s => {
                const idStr = String(s.id);
                if (!this.stato.skuAssociateProdottoCorrente.includes(idStr)) {
                    this.stato.skuAssociateProdottoCorrente.push(idStr);
                }
            });
        }
        if (node.tipo === 'COMPOSTO' && node.figli) {
            node.figli.forEach(f => this.raccogliSkuDaAlbero(f));
        }
    },

    /**
     * Genera la visualizzazione dell'intero albero del prodotto (Tree Editor) aggiornando il DOM.
     * @param {Object} rootNode - Il nodo radice dell'albero.
     */
    renderTreeView: function(rootNode) {
        const container = document.getElementById('dettaglio-content');
        container.innerHTML = '';
        const ui = this.buildNodeUI(rootNode, true);
        container.appendChild(ui);
    },

    /**
     * Costruisce ricorsivamente l'interfaccia utente (HTML) per un singolo nodo dell'albero.
     * Gestisce le differenze di visualizzazione tra nodo SEMPLICE e nodo COMPOSTO.
     * @param {Object} node - Il nodo dati da renderizzare.
     * @param {boolean} [isRoot=false] - Indica se il nodo corrente è la radice dell'albero.
     * @returns {HTMLElement} L'elemento DOM che rappresenta il nodo.
     */
    buildNodeUI: function(node, isRoot = false) {
        let tpl;
        if (node.tipo === 'COMPOSTO') {
            tpl = document.getElementById('tpl-tree-composto').content.cloneNode(true);
            const div = tpl.querySelector('.tree-node');
            div.dataset.id = node.id;
            div.dataset.tipo = 'COMPOSTO';
            tpl.querySelector('.tree-codice').textContent = node.codice;
            tpl.querySelector('.tree-nome').textContent = node.nome;
            tpl.querySelector('.tree-pmin').textContent = node.prezzoMin || 0;
            tpl.querySelector('.tree-pmax').textContent = node.prezzoMax || 0;
            
            if(isRoot) tpl.querySelector('.btn-unlink').remove();
            
            const childrenContainer = tpl.querySelector('.tree-children');
            if (node.figli && node.figli.length > 0) {
                node.figli.forEach(f => childrenContainer.appendChild(this.buildNodeUI(f)));
            }
            return div;
        } else {
            tpl = document.getElementById('tpl-tree-semplice').content.cloneNode(true);
            const div = tpl.querySelector('.tree-node');
            div.dataset.id = node.id;
            div.dataset.tipo = 'SEMPLICE';
            tpl.querySelector('.tree-codice').textContent = node.codice;
            tpl.querySelector('.tree-nome').textContent = node.nome;
            
            if(isRoot) tpl.querySelector('.btn-unlink').remove();
            
            const skusContainer = tpl.querySelector('.tree-skus');
            if (node.skus && node.skus.length > 0) {
                node.skus.forEach(s => skusContainer.appendChild(this.buildSkuUI(s, node.id)));
            }
            return div;
        }
    },

    /**
     * Costruisce l'interfaccia utente (HTML) per mostrare una singola SKU associata a un nodo semplice.
     * @param {Object} sku - L'oggetto contenente le informazioni della SKU.
     * @param {number|string} parentId - L'ID del nodo padre (SEMPLICE) a cui è associata.
     * @returns {HTMLElement} L'elemento DOM che rappresenta la SKU nell'albero.
     */
    buildSkuUI: function(sku, parentId) {
        const tpl = document.getElementById('tpl-tree-sku').content.cloneNode(true);
        const div = tpl.querySelector('.tree-node');
        div.dataset.skuId = sku.id;
        div.dataset.parentId = parentId;
        tpl.querySelector('.tree-codice').textContent = sku.codice;
        tpl.querySelector('.tree-nome').textContent = sku.nome;
        tpl.querySelector('.tree-prezzo').textContent = sku.prezzo;
        return div;
    },

    /**
     * Aggiunge un'azione (es. creazione, modifica, scollegamento) alla coda delle operazioni pendenti
     * che verranno applicate in blocco al momento del salvataggio del Tree Editor.
     * @param {Object} action - L'oggetto contenente i dettagli dell'azione da accodare.
     */
    enqueueAction: function(action) {
        if(!this.stato.pendingActions) this.stato.pendingActions = [];
        this.stato.pendingActions.push(action);
        this.mostraMessaggio("Modifica in attesa di salvataggio", "success");
    },

    /**
     * Gestisce l'azione di "scollegamento" di un nodo figlio dal suo padre.
     * Aggiunge l'azione in coda e rimuove visivamente il nodo dal DOM.
     * @param {HTMLElement} nodeDiv - L'elemento DOM che rappresenta il nodo figlio da scollegare.
     */
    handleTreeUnlink: function(nodeDiv) {
        const id = nodeDiv.dataset.id;
        this.enqueueAction({ action: 'UNLINK_NODE', id: id });
        nodeDiv.remove();
    },

    /**
     * Gestisce l'azione di eliminazione di un intero nodo dell'albero.
     * Aggiunge l'azione di "DELETE_NODE" alla coda pendente e lo rimuove visivamente.
     * @param {HTMLElement} nodeDiv - L'elemento DOM del nodo da eliminare.
     */
    handleTreeDelete: function(nodeDiv) {
        const id = nodeDiv.dataset.id;
        this.enqueueAction({ action: 'DELETE_NODE', id: id });
        nodeDiv.remove();
    },

    /**
     * Gestisce l'azione di scollegamento di una SKU da un nodo semplice.
     * Aggiorna la coda delle azioni pendenti e aggiorna l'elenco locale delle SKU.
     * @param {HTMLElement} skuDiv - L'elemento DOM che modella visivamente la SKU da scollegare.
     */
    handleTreeUnlinkSku: function(skuDiv) {
        const skuId = skuDiv.dataset.skuId;
        const parentId = skuDiv.dataset.parentId;
        this.enqueueAction({ action: 'UNLINK_SKU', skuId: skuId, parentId: parentId });
        skuDiv.remove();
        // Rimuovi dalla lista locale
        this.stato.skuAssociateProdottoCorrente = this.stato.skuAssociateProdottoCorrente.filter(id => id !== String(skuId));
    },

    /**
     * Elimina definitivamente una SKU associata al prodotto.
     * Effettua immediatamente la cancellazione tramite l'API backend se la SKU esiste già nel database,
     * altrimenti la rimuove soltanto dalla coda locale.
     * @param {HTMLElement} skuDiv - L'elemento DOM della SKU da eliminare.
     */
    handleTreeDeleteSku: async function(skuDiv) {
        const skuId = skuDiv.dataset.skuId;
        
        // Se l'ID è temporaneo (SKU non ancora salvata), basta rimuoverla dalla coda e dal DOM
        if (String(skuId).startsWith('temp_')) {
            this.stato.pendingActions = (this.stato.pendingActions || []).filter(a =>
                !(a.tempId === skuId) && !(a.skuId === skuId)
            );
            skuDiv.remove();
            // Rimuovi dalla lista locale
            this.stato.skuAssociateProdottoCorrente = this.stato.skuAssociateProdottoCorrente.filter(id => id !== String(skuId));
            this.mostraMessaggio('SKU rimossa (non era ancora salvata)', 'success');
            return;
        }
        
        if (!confirm('Eliminare definitivamente questa SKU? L\'operazione eliminerà anche le configurazioni cliente ad essa associate.')) return;
        
        try {
            // Elimina la SKU definitivamente e cancella a cascata configurazioni e associazioni
            await api.deleteSku(parseInt(skuId));
            
            skuDiv.remove();
            // Rimuovi dalla lista locale
            this.stato.skuAssociateProdottoCorrente = this.stato.skuAssociateProdottoCorrente.filter(id => id !== String(skuId));
            
            // Rimuovi eventuali azioni pendenti che la riguardavano
            this.stato.pendingActions = (this.stato.pendingActions || []).filter(a =>
                !(a.skuId === String(skuId) || a.skuId === skuId)
            );
            
            // Aggiorna anche la lista globale delle SKU
            this.stato.skusDisponibili = this.stato.skusDisponibili.filter(s => s.id !== parseInt(skuId));
            this.renderSkuCheckboxList();
            this.mostraMessaggio('SKU eliminata definitivamente', 'success');
        } catch (err) {
            this.mostraMessaggio('Errore eliminazione SKU: ' + err.message, 'error');
        }
    },

    /**
     * Gestisce la modifica inline (Inline Edit) dei valori di un nodo dell'albero o di una SKU.
     * Aggiunge un'azione di aggiornamento (UPDATE_NODE o UPDATE_SKU) alla coda delle operazioni pendenti
     * registrando i nuovi valori di nome, codice o prezzo.
     * @param {HTMLElement} span - L'elemento DOM reso modificabile con l'attributo `contenteditable`.
     */
    handleTreeInlineEdit: function(span) {
        const nodeDiv = span.closest('.tree-node');
        if(!nodeDiv) return;
        const isSku = nodeDiv.classList.contains('tree-sku');
        const id = isSku ? nodeDiv.dataset.skuId : nodeDiv.dataset.id;
        const val = span.textContent.trim();
        
        let updateAction = this.stato.pendingActions.find(a => 
            (isSku ? a.action === 'UPDATE_SKU' : a.action === 'UPDATE_NODE') && (a.id == id || a.tempId == id)
        );
        
        if (!updateAction) {
            updateAction = isSku ? { action: 'UPDATE_SKU', id: id } : { action: 'UPDATE_NODE', id: id };
            this.stato.pendingActions.push(updateAction);
        }
        
        if (span.classList.contains('tree-nome')) updateAction.nome = val;
        else if (span.classList.contains('tree-codice')) updateAction.codice = val;
        else if (span.classList.contains('tree-pmin')) updateAction.prezzoMin = val;
        else if (span.classList.contains('tree-pmax')) updateAction.prezzoMax = val;
        else if (span.classList.contains('tree-prezzo')) updateAction.prezzo = val;
        
        this.mostraMessaggio("Modifica in attesa", "success");
    },

    /**
     * Calcola il livello (profondità) di un nodo nell'albero DOM del Tree Editor.
     * La radice è al livello 1, i suoi figli al livello 2, ecc.
     * @param {HTMLElement} nodeDiv - L'elemento DOM .tree-node di cui calcolare il livello.
     * @returns {number} Il livello del nodo nell'albero (1 = radice).
     */
    getNodeLevel: function(nodeDiv) {
        let level = 0;
        let el = nodeDiv;
        while (el) {
            if (el.classList && el.classList.contains('tree-node') && !el.classList.contains('tree-sku')) {
                level++;
            }
            el = el.parentElement;
        }
        return level;
    },

    /**
     * Prepara il form modale per l'aggiunta di un nodo figlio a un nodo COMPOSTO.
     * Controlla che il livello del padre non superi la profondità massima consentita (3 livelli strutturali).
     * @param {HTMLElement} parentDiv - L'elemento DOM che rappresenta il nodo padre.
     */
    handleTreeAddChild: function(parentDiv) {
        // Controlla la profondità: se il padre è al livello 3, non si può aggiungere un figlio
        const parentLevel = this.getNodeLevel(parentDiv);
        if (parentLevel >= 3) {
            this.mostraMessaggio("Impossibile aggiungere: la profondità massima dell'albero (3 livelli strutturali) è stata raggiunta.", "error");
            return;
        }

        const parentId = parentDiv.dataset.id;
        document.getElementById('add-child-parent-id').value = parentId;
        document.getElementById('form-add-child').reset();
        document.getElementById('modal-add-child').style.display = 'flex';
    },

    /**
     * Gestisce l'invio del form per aggiungere un nuovo nodo figlio all'albero.
     * Genera un ID temporaneo e accoda l'azione (CREATE_NODE) prima di aggiornare visivamente il DOM.
     * Verifica che la profondità massima (3 livelli) non venga superata.
     * @param {Event} e - L'evento originato dal submit del form.
     */
    submitTreeAddChild: function(e) {
        e.preventDefault();
        const parentId = document.getElementById('add-child-parent-id').value;
        const type = document.getElementById('add-child-tipo').value;
        const codice = document.getElementById('add-child-codice').value;
        const nome = document.getElementById('add-child-nome').value;

        // Doppio check profondità prima di accodare l'azione
        const parentDiv = document.querySelector(`.tree-node[data-id="${parentId}"]`);
        if (parentDiv) {
            const parentLevel = this.getNodeLevel(parentDiv);
            if (parentLevel >= 3) {
                this.mostraMessaggio("Impossibile aggiungere: la profondità massima dell'albero (3 livelli strutturali) è stata raggiunta.", "error");
                document.getElementById('modal-add-child').style.display = 'none';
                return;
            }
        }
        
        const tempId = 'temp_' + Date.now();
        let action = { action: 'CREATE_NODE', tempId: tempId, parentId: parentId, codice: codice, nome: nome };
        let newNode;
        
        if (type === 'COMPOSTO') {
            action.tipo = 'COMPOSTO';
            newNode = { id: tempId, tipo: 'COMPOSTO', codice: codice, nome: nome, figli: [] };
        } else {
            action.tipo = 'SEMPLICE';
            newNode = { id: tempId, tipo: 'SEMPLICE', codice: codice, nome: nome, skus: [] };
        }
        
        this.enqueueAction(action);
        
        // Trova il div padre corretto e aggiungi
        if (parentDiv) {
            const childrenContainer = parentDiv.querySelector('.tree-children');
            childrenContainer.appendChild(this.buildNodeUI(newNode, false));
        }
        
        document.getElementById('modal-add-child').style.display = 'none';
    },

    /**
     * Prepara il form modale per associare una SKU (esistente o nuova) a un nodo SEMPLICE.
     * Popola la select delle SKU esistenti escludendo quelle già associate al prodotto corrente
     * per evitare doppioni a livello di validazione front-end.
     * @param {HTMLElement} parentDiv - L'elemento DOM che rappresenta il nodo SEMPLICE.
     */
    handleTreeAddSku: function(parentDiv) {
        const parentId = parentDiv.dataset.id;
        document.getElementById('add-sku-parent-id').value = parentId;
        document.getElementById('form-add-sku').reset();
        document.getElementById('add-sku-opzione').value = 'NEW';
        document.getElementById('add-sku-new-fields').style.display = 'block';
        document.getElementById('add-sku-exist-fields').style.display = 'none';
        
        // Usa la lista locale come fonte di verità per filtrare le SKU già associate
        const listaAssociate = this.stato.skuAssociateProdottoCorrente || [];
        
        // Popola la select delle SKU esistenti escludendo quelle nella lista locale
        const select = document.getElementById('add-sku-select');
        select.innerHTML = '';
        
        let availableSkus = [];
        if (this.stato.skusDisponibili) {
            availableSkus = this.stato.skusDisponibili.filter(sku => !listaAssociate.includes(String(sku.id)));
        }
        
        const submitBtn = document.querySelector('#form-add-sku button[type="submit"]');
        
        if (availableSkus.length > 0) {
            availableSkus.forEach(sku => {
                const opt = document.createElement('option');
                opt.value = sku.id;
                opt.textContent = `[${sku.codice}] ${sku.nome} - €${sku.prezzo}`;
                select.appendChild(opt);
            });
            if (submitBtn) submitBtn.disabled = false;
        } else {
            const opt = document.createElement('option');
            opt.value = "";
            opt.textContent = "Nessuna SKU disponibile (o tutte già associate)";
            select.appendChild(opt);
        }
        
        document.getElementById('modal-add-sku').style.display = 'flex';
    },

    /**
     * Gestisce l'invio del form per aggiungere/associare una SKU a un nodo SEMPLICE.
     * Supporta sia la creazione di una SKU ex-novo (con ID temporaneo) sia l'associazione di una SKU esistente.
     * Accoda l'azione corrispondente (CREATE_SKU o ADD_SKU) e aggiorna il DOM locale.
     * @param {Event} e - L'evento originato dal submit del form.
     */
    submitTreeAddSku: function(e) {
        e.preventDefault();
        const parentId = document.getElementById('add-sku-parent-id').value;
        const opzione = document.getElementById('add-sku-opzione').value;
        const parentDiv = document.querySelector(`.tree-node[data-id="${parentId}"]`);
        
        if (opzione === 'NEW') {
            const tempId = 'temp_sku_' + Date.now();
            const codice = document.getElementById('add-sku-codice').value;
            const nome = document.getElementById('add-sku-nome').value;
            const prezzo = document.getElementById('add-sku-prezzo').value;
            
            if(!codice || !nome || !prezzo) return;
            
            this.enqueueAction({ action: 'CREATE_SKU', tempId: tempId, parentId: parentId, codice: codice, nome: nome, prezzo: prezzo });
            const skuUI = this.buildSkuUI({ id: tempId, codice: codice, nome: nome, prezzo: prezzo }, parentId);
            if (parentDiv) parentDiv.querySelector('.tree-skus').appendChild(skuUI);
            // Aggiungi l'ID temporaneo alla lista locale
            this.stato.skuAssociateProdottoCorrente.push(String(tempId));
            
        } else {
            const skuId = document.getElementById('add-sku-select').value;
            if(!skuId) return;
            this.enqueueAction({ action: 'ADD_SKU', parentId: parentId, skuId: skuId });
            
            const existingSku = this.stato.skusDisponibili.find(s => s.id == skuId) || { id: skuId, codice: '?', nome: 'SKU Aggiunta', prezzo: '0' };
            const skuUI = this.buildSkuUI(existingSku, parentId);
            if (parentDiv) parentDiv.querySelector('.tree-skus').appendChild(skuUI);
            // Aggiungi l'ID reale alla lista locale
            this.stato.skuAssociateProdottoCorrente.push(String(skuId));
        }
        
        document.getElementById('modal-add-sku').style.display = 'none';
    },

    /**
     * Effettua il salvataggio in blocco sul server dell'albero modificato (Tree Editor).
     * Invia l'intero array delle azioni pendenti (`pendingActions`) all'API di sincronizzazione.
     * Ricarica il prodotto aggiornato da server in caso di successo.
     */
    handleSalvaTree: async function() {
        if (!this.stato.pendingActions || this.stato.pendingActions.length === 0) {
            this.mostraMessaggio("Nessuna modifica da salvare.", "info");
            return;
        }
        
        const btn = document.getElementById('btn-salva-tree');
        btn.disabled = true;
        
        try {
            const res = await api.syncTree(this.stato.pendingActions);
            if(res.success) {
                this.mostraMessaggio("Tutte le modifiche salvate con successo!", "success");
                this.stato.pendingActions = [];
                // Ricarichiamo l'albero per essere sicuri
                const rootDiv = document.querySelector('.tree-node');
                if (rootDiv && rootDiv.dataset.id && !rootDiv.dataset.id.startsWith('temp_')) {
                    this.renderProdottoCreato({ id: rootDiv.dataset.id });
                } else {
                    document.getElementById('dettaglio-content').innerHTML = '';
                    document.getElementById('dettaglio-creazione').style.display = 'none';
                }
            }
        } catch (e) {
            this.mostraMessaggio("Errore durante il salvataggio: " + e.message, "error");
        } finally {
            btn.disabled = false;
        }
    },

    /**
     * Elimina in modo irreversibile una SKU dal catalogo e aggiorna le liste locali.
     * Utilizzato dalla vista di dettaglio della singola SKU (fuori dal contesto dell'albero).
     * @param {string|number} idStr - L'ID univoco della SKU da rimuovere.
     */
    handleEliminaSku: async function(idStr) {
        const id = parseInt(idStr);
        if (!confirm("Sei sicuro di voler eliminare questa SKU?")) return;
        try {
            await api.deleteSku(id);
            this.mostraMessaggio("SKU eliminata", "success");
            this.stato.skusDisponibili = this.stato.skusDisponibili.filter(s => s.id !== id);
            this.renderSkuCheckboxList();
            document.getElementById('dettaglio-content').innerHTML = '';
            document.getElementById('dettaglio-creazione').style.display = 'none';
            // Rimuovi anche l'eventuale item dalla lista dei risultati di ricerca
            const searchItem = document.querySelector(`.search-result-item[data-id="${id}"]`);
            if (searchItem) searchItem.remove();
        } catch (err) {
            this.mostraMessaggio(err.message, "error");
        }
    },

    /**
     * Gestisce la modifica inline (Inline Edit) dei valori testuali di una SKU già esistente e salvata.
     * Effettua una chiamata API per aggiornare il singolo campo.
     * @param {HTMLElement} span - L'elemento DOM reso modificabile.
     */
    handleSkuInlineEdit: async function(span) {
        const card = span.closest('.sku-created-card');
        if (!card) return;
        const skuId = parseInt(card.dataset.skuId);
        const field = span.dataset.field;
        const newValue = span.textContent.trim();
        
        const skuIndex = this.stato.skusDisponibili.findIndex(s => s.id === skuId);
        if (skuIndex === -1) return;
        const skuCopy = { ...this.stato.skusDisponibili[skuIndex] };
        
        // Verifica variazioni
        if (field === 'prezzo' || field === 'codice' || field === 'quantitaDisponibile') {
            const num = parseFloat(newValue);
            if (isNaN(num) || num < 0) {
                span.textContent = skuCopy[field];
                this.mostraMessaggio("Valore numerico non valido", "error");
                return;
            }
            if (num === skuCopy[field]) return; // invariato
            skuCopy[field] = num;
        } else {
            if (newValue === skuCopy[field]) return;
            skuCopy[field] = newValue;
        }

        const formData = new FormData();
        formData.append('id', skuCopy.id);
        formData.append('codice', skuCopy.codice);
        formData.append('nome', skuCopy.nome);
        formData.append('descrizioneTecnica', skuCopy.descrizioneTecnica || '');
        formData.append('prezzo', skuCopy.prezzo);
        formData.append('quantitaDisponibile', skuCopy.quantitaDisponibile || 0);
        formData.append('fotografiaUrlOriginale', skuCopy.fotografia || '');

        try {
            span.style.opacity = '0.5';
            const res = await api.updateSku(formData);
            if (res.data) {
                this.stato.skusDisponibili[skuIndex] = res.data;
                span.textContent = res.data[field] || '';
                this.mostraMessaggio("Campo aggiornato con successo", "success");
                this.renderSkuCheckboxList();
            }
        } catch (err) {
            span.textContent = this.stato.skusDisponibili[skuIndex][field] || '';
            this.mostraMessaggio("Errore aggiornamento: " + err.message, "error");
        } finally {
            span.style.opacity = '1';
        }
    },

    /**
     * Gestisce l'invio del form per la creazione di un nuovo Prodotto SEMPLICE.
     * Verifica la presenza di almeno una SKU associata prima di inviare il payload al server.
     * @param {Event} e - L'evento di submit del form.
     */
    handleSubmitSemplice: async function(e) {
        e.preventDefault();
        const form = e.target;
        const checkboxes = form.querySelectorAll('input[name="skus_selezionate"]:checked');
        const errSpan = document.getElementById('error-semplice-sku');
        
        if (checkboxes.length === 0) {
            errSpan.style.display = 'block';
            return;
        }
        errSpan.style.display = 'none';

        const payload = {
            codice: parseInt(form.elements['codice'].value),
            nome: form.elements['nome'].value,
            tipo: 'SEMPLICE',
            skus: Array.from(checkboxes).map(chk => ({ id: parseInt(chk.value) }))
        };

        const submitBtn = form.querySelector('button[type="submit"]');
        submitBtn.disabled = true;

        try {
            const res = await api.saveTree(payload);
            if (res.success) {
                this.mostraMessaggio("Prodotto Semplice salvato con successo", "success");
                form.reset();
                this.aggiornaOrfani(); // Aggiorna elenco composti
                // Il server potrebbe non restituire l'oggetto completo, in quel caso passiamo i dati base per renderizzare:
                const savedProd = res.data || { id: res.id_prodotto || '?', tipo: 'SEMPLICE', codice: payload.codice, nome: payload.nome };
                this.renderProdottoCreato(savedProd);
            }
        } catch (err) {
            this.mostraMessaggio("Errore server: " + err.message, "error");
        } finally {
            submitBtn.disabled = false;
        }
    },

    /**
     * Gestisce l'invio del form per la creazione di un nuovo Prodotto COMPOSTO.
     * Estrae i figli orfani selezionati dalle checkbox e invia la richiesta all'API.
     * @param {Event} e - L'evento di submit del form.
     */
    handleCreaComposto: async function(e) {
        e.preventDefault();
        const form = e.target;
        const checkboxes = form.querySelectorAll('input[name="orfani_selezionati"]:checked');

        const prezzoMin = parseFloat(form.elements['prezzo_min'].value) || 0;
        const prezzoMax = parseFloat(form.elements['prezzo_max'].value) || 0;

        // Validazione V2: prezzoMax > prezzoMin
        if (prezzoMax <= prezzoMin) {
            this.mostraMessaggio('Il prezzo massimo deve essere strettamente maggiore del prezzo minimo', 'error');
            return;
        }

        // Validazione V1: prezzoMin >= somma prezzoMin dei figli selezionati
        let sumMin = 0;
        checkboxes.forEach(chk => { sumMin += parseFloat(chk.dataset.prezzoMin || 0); });
        if (prezzoMin < sumMin) {
            this.mostraMessaggio(
                `Il prezzo minimo deve essere almeno ${sumMin.toFixed(2)} € (somma dei prezzi min dei sottoprodotti)`,
                'error'
            );
            return;
        }

        const payload = {
            codice: parseInt(form.elements['codice'].value),
            nome: form.elements['nome'].value,
            descrizione: form.elements['descrizione'].value,
            prezzoMin: prezzoMin,
            prezzoMax: prezzoMax,
            tipo: 'COMPOSTO',
            figli: Array.from(checkboxes).map(chk => {
                const orfano = this.stato.orfaniDisponibili.find(o => o.id == chk.value);
                return { id: orfano.id, tipo: orfano.tipo };
            })
        };

        const submitBtn = form.querySelector('button[type="submit"]');
        submitBtn.disabled = true;

        try {
            const res = await api.saveTree(payload);
            if (res.success) {
                this.mostraMessaggio("Prodotto Composto salvato con successo", "success");
                form.reset();
                document.getElementById('prezzo-riepilogo').style.display = 'none';
                document.getElementById('sum-prezzo-min').textContent = '0.00';
                document.getElementById('sum-prezzo-max').textContent = '0.00';
                this.aggiornaOrfani();
                const savedProd = res.data || { 
                    id: res.id_prodotto || '?', tipo: 'COMPOSTO', codice: payload.codice, nome: payload.nome,
                    descrizione: payload.descrizione, prezzoMin: payload.prezzoMin, prezzoMax: payload.prezzoMax
                };
                this.renderProdottoCreato(savedProd);
            }
        } catch (err) {
            this.mostraMessaggio("Errore server: " + err.message, "error");
        } finally {
            submitBtn.disabled = false;
        }
    },

    /**
     * Aggiorna la lista locale dei prodotti orfani (senza padre) recuperandoli dal server.
     * Successivamente, forza il re-render della lista delle checkbox associate.
     */
    aggiornaOrfani: async function() {
        try {
            const res = await api.fetchJson('api/prodotto?orfani=true');
            if (res.data) {
                this.stato.orfaniDisponibili = res.data;
                this.renderOrfaniCheckboxList();
            }
        } catch (e) {
            console.error("Failed to fetch orfani", e);
        }
    },

    /**
     * Gestisce la ricerca full-text globale per SKU, Prodotti Semplici e Composti.
     * Invia la query all'API di ricerca e renderizza dinamicamente i risultati con i rispettivi badge.
     * @param {Event} e - L'evento di submit del form di ricerca.
     */
    handleSearch: async function(e) {
        e.preventDefault();
        const q = e.target.elements['q'].value;
        const container = document.getElementById('risultati-container');
        container.innerHTML = '<div style="text-align:center; padding: 2rem;"><span class="spinner"></span></div>';
        try {
            const res = await api.search(q);
            if (!res.data || res.data.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <i class="fa-solid fa-box-open empty-icon"></i>
                        <p>Nessun risultato trovato per "${this.escapeHtml(q)}".</p>
                    </div>`;
                return;
            }
            container.innerHTML = '';
            res.data.forEach(item => {
                const tpl = document.getElementById('tpl-search-result').content.cloneNode(true);
                const itemEl = tpl.querySelector('.search-result-item');
                itemEl.dataset.id = item.id;
                itemEl.dataset.tipo = item.tipo;
                tpl.querySelector('.result-name').textContent = item.nome;
                tpl.querySelector('.result-code').textContent = `Cod: ${item.codice}`;
                const badge = tpl.querySelector('.badge');
                if (item.tipo === 'COMPOSTO') {
                    badge.textContent = 'Composto';
                    badge.classList.add('badge-composto');
                } else if (item.tipo === 'SEMPLICE') {
                    badge.textContent = 'Semplice';
                    badge.classList.add('badge-semplice');
                } else {
                    badge.textContent = 'SKU';
                    badge.classList.add('badge-sku');
                }
                container.appendChild(tpl);
            });
        } catch (err) {
            container.innerHTML = `<div class="field-error">Errore: ${this.escapeHtml(err.message)}</div>`;
        }
    },

    /**
     * Espande un elemento cliccato dalla lista dei risultati di ricerca, mostrandone il dettaglio completo
     * (es. l'albero per i prodotti o i dati per la SKU).
     * @param {HTMLElement} resultItem - L'elemento DOM cliccato corrispondente al risultato.
     * @param {string|number} idStr - L'ID del risultato.
     * @param {string} tipo - Il tipo del risultato ("SKU", "SEMPLICE" o "COMPOSTO").
     */
    handleExpandSearchResult: async function(resultItem, idStr, tipo) {
        const id = parseInt(idStr);
        if (tipo === 'SKU') {
            let sku = this.stato.skusDisponibili.find(s => s.id === id);
            if (!sku) {
                try {
                    const skuRes = await api.getAllSkus();
                    if (skuRes && skuRes.data) {
                        this.stato.skusDisponibili = skuRes.data;
                        this.renderSkuCheckboxList();
                        sku = this.stato.skusDisponibili.find(s => s.id === id);
                    }
                } catch (err) {
                    console.error("Errore nel recupero delle SKU:", err);
                }
            }
            if (sku) {
                this.assureDetailContainerLocation();
                this.renderSkuCreata(sku);
                document.getElementById('dettaglio-creazione').scrollIntoView({ behavior: 'smooth' });
            } else {
                this.mostraMessaggio("Dettagli SKU non trovati.", "error");
            }
            return;
        }

        // Se è un PRODOTTO (SEMPLICE o COMPOSTO)
        this.assureDetailContainerLocation();
        await this.renderProdottoCreato({ id: id });
        document.getElementById('dettaglio-creazione').scrollIntoView({ behavior: 'smooth' });
    },

    /**
     * Elimina in modo irreversibile un intero Prodotto (Semplice o Composto) dal database.
     * Rimuove il prodotto dai risultati di ricerca, dai dettagli aperti e aggiorna la lista degli orfani.
     * @param {string|number} id - L'ID del prodotto da eliminare.
     */
    deleteProdottoFromDB: async function(id) {
        if (!confirm("ATTENZIONE: Eliminare questo prodotto dal database? L'operazione è irreversibile.")) return;
        try {
            await api.deleteProdotto(id);
            this.mostraMessaggio("Prodotto eliminato definitivamente.", "success");
            const item = document.querySelector(`.search-result-item[data-id="${id}"]`);
            if (item) item.remove();
            
            // Pulisci area dettaglio se stavamo guardando questo
            const createdCard = document.querySelector(`.prodotto-created-card[data-prod-id="${id}"]`);
            if (createdCard) {
                document.getElementById('dettaglio-content').innerHTML = '';
                document.getElementById('dettaglio-creazione').style.display = 'none';
            }
            
            this.aggiornaOrfani();
        } catch (err) {
            this.mostraMessaggio("Errore eliminazione: " + err.message, "error");
        }
    },
    
    /**
     * Mostra un messaggio (Toast) non bloccante in sovraimpressione (in basso a destra).
     * @param {string} testo - Il messaggio da mostrare all'utente.
     * @param {string} [tipo='success'] - La classe CSS per il tipo di messaggio ('success', 'error', 'warning', 'info').
     */
    mostraMessaggio: function(testo, tipo = 'success') {
        const container = document.getElementById('toast-container');
        if (!container) return;
        const toast = document.createElement('div');
        toast.className = `toast toast-${tipo}`;
        let icon = 'info-circle';
        if (tipo === 'success') icon = 'circle-check';
        if (tipo === 'error') icon = 'circle-xmark';
        if (tipo === 'warning') icon = 'triangle-exclamation';
        toast.innerHTML = `<i class="fa-solid fa-${icon}" style="margin-right: 0.5rem;"></i>${this.escapeHtml(testo)}`;
        container.appendChild(toast);
        setTimeout(() => { if (toast.parentNode) toast.remove(); }, 4000);
    },

    /**
     * Esegue l'escape delle stringhe testuali per prevenire attacchi XSS (Cross-Site Scripting)
     * quando i valori vengono inseriti nell'HTML tramite `innerHTML`.
     * @param {string} str - La stringa da sanificare.
     * @returns {string} La stringa resa sicura.
     */
    escapeHtml: function(str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }
};

document.addEventListener('DOMContentLoaded', () => {
    AppFornitore.init();
});
