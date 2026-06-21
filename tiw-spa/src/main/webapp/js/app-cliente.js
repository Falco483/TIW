/**
 * AppCliente - Orchestratore SPA per l'interfaccia Cliente.
 * Gestisce lo stato in RAM, il routing tra sezioni e il rendering del DOM.
 */
const AppCliente = {

    stato: {
        sezioneAttiva: 'catalogo',
        // Albero prodotto corrente (per la sezione configura)
        alberoCorrente: null,
        // Modalità form: 'crea' | 'modifica'
        modalitaForm: 'crea',
        // ID configurazione in modifica (null se modalità crea)
        idConfigInModifica: null,
    },

    // -------------------------------------------------------------------------
    // Init
    // -------------------------------------------------------------------------

    /**
     * Inizializza l'applicazione SPA Cliente: aggancia gli eventi, recupera le
     * info dell'utente corrente e carica il catalogo iniziale.
     */
    init: async function () {
        this.bindGlobalEvents();

        try {
            const userRes = await api.getUser().catch(() => ({ username: 'Cliente' }));

            if (userRes && userRes.csrfToken) {
                const meta = document.querySelector('meta[name="_csrf"]');
                if (meta) meta.setAttribute('content', userRes.csrfToken);
            }

            const nomeStr = userRes.nome ? `${userRes.nome} ${userRes.cognome}` : userRes.username;
            document.getElementById('sidebarUserName').textContent = nomeStr;

        } catch (err) {
            this.mostraMessaggio('Errore caricamento dati utente.', 'error');
        }

        // Carica il catalogo nella sezione iniziale
        await this.caricaCatalogo();
    },

    // -------------------------------------------------------------------------
    // Event binding
    // -------------------------------------------------------------------------

    /**
     * Configura i listener per gli eventi DOM (click e submit) dell'applicazione.
     * Gestisce la navigazione tra le sezioni e le azioni dell'utente.
     */
    bindGlobalEvents: function () {
        // Pulsante unico di navigazione: alterna tra catalogo e configurazioni
        const btnNavToggle = document.getElementById('btn-nav-toggle');
        if (btnNavToggle) {
            btnNavToggle.addEventListener('click', () => {
                if (this.stato.sezioneAttiva === 'catalogo' || this.stato.sezioneAttiva === 'configura') {
                    this.switchSection('configurazioni');
                    this.caricaConfigurazioni();
                    btnNavToggle.innerHTML = '<i class="fa-solid fa-store"></i> Catalogo';
                } else {
                    this.switchSection('catalogo');
                    btnNavToggle.innerHTML = '<i class="fa-solid fa-list-check"></i> Le mie Configurazioni';
                }
            });
        }

        // Catalogo: click su un prodotto → apre sezione configura
        document.getElementById('catalogo-container').addEventListener('click', (e) => {
            const item = e.target.closest('.search-result-item[data-codice]');
            if (item) this.apriConfigurazione(parseInt(item.dataset.codice), item.dataset.nome);
        });

        // Sezione configura: pulsanti navigazione e submit
        document.getElementById('btn-torna-catalogo').addEventListener('click', () => {
            this.switchSection('catalogo');
        });
        document.getElementById('btn-annulla-config').addEventListener('click', () => {
            this.switchSection('catalogo');
        });
        document.getElementById('form-configura').addEventListener('submit', (e) => {
            this.handleSalvaConfigurazione(e);
        });

        // Albero configurazione: espandi nodo composto on-demand
        document.getElementById('albero-container').addEventListener('click', (e) => {
            const btnEspandi = e.target.closest('.btn-espandi');
            if (btnEspandi) {
                const nodoDiv = btnEspandi.closest('.config-node-composto');
                if (nodoDiv) this.espandiNodo(nodoDiv);
            }
        });

        // Sezione dettaglio: torna alle configurazioni
        document.getElementById('btn-torna-configurazioni').addEventListener('click', () => {
            this.switchSection('configurazioni');
        });

        // Sezione configurazioni: nuova configurazione → vai al catalogo
        document.getElementById('btn-nuova-config').addEventListener('click', () => {
            this.switchSection('catalogo');
        });

        // Lista configurazioni: delegazione per azioni (dettaglio, modifica, clona, elimina)
        document.getElementById('configurazioni-container').addEventListener('click', (e) => {
            const row = e.target.closest('.search-result-item[data-id]');
            if (!row) return;
            const id = parseInt(row.dataset.id);

            if (e.target.closest('.btn-dettaglio')) {
                this.apriDettaglio(id);
            } else if (e.target.closest('.btn-modifica')) {
                this.apriModifica(id);
            } else if (e.target.closest('.btn-clona')) {
                this.cloneConfigurazione(id);
            } else if (e.target.closest('.btn-elimina')) {
                this.eliminaConfigurazione(id, row);
            }
        });
    },

    // -------------------------------------------------------------------------
    // Routing sezioni
    // -------------------------------------------------------------------------

    /**
     * Naviga verso la sezione specificata dell'applicazione nascondendo le altre.
     * @param {string} sectionId - L'ID della sezione da attivare (es. 'catalogo', 'configura', 'configurazioni', 'dettaglio').
     */
    switchSection: function (sectionId) {
        document.querySelectorAll('.app-section').forEach(sec => sec.classList.remove('active'));
        document.getElementById(`section-${sectionId}`).classList.add('active');
        this.stato.sezioneAttiva = sectionId;

        // Aggiorna il testo del pulsante di navigazione in base alla sezione
        const btnNavToggle = document.getElementById('btn-nav-toggle');
        if (btnNavToggle) {
            if (sectionId === 'catalogo' || sectionId === 'configura') {
                btnNavToggle.innerHTML = '<i class="fa-solid fa-list-check"></i> Le mie Configurazioni';
            } else {
                btnNavToggle.innerHTML = '<i class="fa-solid fa-store"></i> Catalogo';
            }
        }
    },

    // -------------------------------------------------------------------------
    // Sezione 1: Catalogo
    // -------------------------------------------------------------------------

    /**
     * Recupera l'elenco dei prodotti radice (composti) disponibili dal server
     * e li visualizza nella griglia del catalogo con i rispettivi range di prezzo.
     * Implementa paginazione lato client: ordine alfabetico decrescente (Z-A),
     * massimo 10 prodotti per pagina, con bottoni Precedenti/Successivi.
     */
    caricaCatalogo: async function () {
        const container = document.getElementById('catalogo-container');
        container.innerHTML = `
            <div class="empty-state">
                <span class="spinner" style="border-color:rgba(0,0,0,0.15);border-top-color:var(--accent);"></span>
                <p style="margin-top:0.75rem;">Caricamento prodotti...</p>
            </div>`;

        try {
            const res = await api.getProdottiRadice();
            const lista = res.data || [];

            if (lista.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <i class="fa-solid fa-box-open empty-icon"></i>
                        <p>Nessun prodotto disponibile nel catalogo.</p>
                    </div>`;
                return;
            }

            // Ordina Z-A (decrescente) per nome
            lista.sort((a, b) => (b.nome || '').localeCompare(a.nome || '', 'it'));

            // Salva la lista completa e inizializza la paginazione
            this.stato.catalogoCompleto = lista;
            this.stato.paginaCorrente = 0;
            this.stato.prodottiPerPagina = 10;
            this.renderPaginaCatalogo();

        } catch (err) {
            container.innerHTML = `<div class="field-error" style="padding:1rem;">Errore: ${this.escapeHtml(err.message)}</div>`;
        }
    },

    /**
     * Renderizza la pagina corrente del catalogo con i bottoni di navigazione.
     */
    renderPaginaCatalogo: function () {
        const lista = this.stato.catalogoCompleto || [];
        const pagina = this.stato.paginaCorrente || 0;
        const perPagina = this.stato.prodottiPerPagina || 10;
        const totalePagine = Math.ceil(lista.length / perPagina);
        const inizio = pagina * perPagina;
        const fine = Math.min(inizio + perPagina, lista.length);
        const paginaCorrente = lista.slice(inizio, fine);

        const container = document.getElementById('catalogo-container');
        container.innerHTML = '';

        paginaCorrente.forEach(p => {
            const tpl = document.getElementById('tpl-catalogo-item').content.cloneNode(true);
            const item = tpl.querySelector('.search-result-item');
            item.dataset.codice = p.codice;
            item.dataset.nome = p.nome;

            tpl.querySelector('.result-name').textContent = p.nome;
            tpl.querySelector('.result-desc').textContent = p.descrizione || '';

            const prezzoMin = p.prezzoMin != null ? parseFloat(p.prezzoMin).toFixed(2) : null;
            const prezzoMax = p.prezzoMax != null ? parseFloat(p.prezzoMax).toFixed(2) : null;
            if (prezzoMin != null && prezzoMax != null) {
                tpl.querySelector('.result-prezzo').textContent = `€${prezzoMin} – €${prezzoMax}`;
            } else {
                tpl.querySelector('.result-prezzo').textContent = '';
            }

            container.appendChild(tpl);
        });

        // Bottoni di navigazione
        if (totalePagine > 1) {
            const navDiv = document.createElement('div');
            navDiv.style.cssText = 'display:flex; justify-content:center; align-items:center; gap:1rem; margin-top:1.5rem; padding:0.5rem;';

            if (pagina > 0) {
                const btnPrec = document.createElement('button');
                btnPrec.className = 'btn btn-ghost';
                btnPrec.style.cssText = 'border:1px solid var(--card-border); padding:0.4rem 1rem;';
                btnPrec.innerHTML = '<i class="fa-solid fa-chevron-left" style="margin-right:0.3rem;"></i> Precedenti';
                btnPrec.addEventListener('click', () => {
                    this.stato.paginaCorrente--;
                    this.renderPaginaCatalogo();
                });
                navDiv.appendChild(btnPrec);
            }

            const infoSpan = document.createElement('span');
            infoSpan.style.cssText = 'color:var(--text-secondary); font-size:0.85rem;';
            infoSpan.textContent = `Pagina ${pagina + 1} di ${totalePagine}`;
            navDiv.appendChild(infoSpan);

            if (pagina < totalePagine - 1) {
                const btnSucc = document.createElement('button');
                btnSucc.className = 'btn btn-ghost';
                btnSucc.style.cssText = 'border:1px solid var(--card-border); padding:0.4rem 1rem;';
                btnSucc.innerHTML = 'Successivi <i class="fa-solid fa-chevron-right" style="margin-left:0.3rem;"></i>';
                btnSucc.addEventListener('click', () => {
                    this.stato.paginaCorrente++;
                    this.renderPaginaCatalogo();
                });
                navDiv.appendChild(btnSucc);
            }

            container.appendChild(navDiv);
        }
    },

    // -------------------------------------------------------------------------
    // Sezione 2: Configurazione prodotto
    // -------------------------------------------------------------------------

    /**
     * Carica l'albero del prodotto e apre la sezione configura (modalità creazione).
     * @param {number} codice - Il codice del prodotto radice.
     * @param {string} nome - Il nome del prodotto (per l'intestazione).
     */
    apriConfigurazione: async function (codice, nome) {
        this.stato.modalitaForm = 'crea';
        this.stato.idConfigInModifica = null;
        this.stato.alberoCorrente = null;

        // Reset form
        document.getElementById('form-configura').reset();
        document.getElementById('configura-errore').style.display = 'none';
        document.getElementById('error-nome-config').style.display = 'none';
        document.getElementById('albero-container').innerHTML = `
            <div class="empty-state">
                <span class="spinner" style="border-color:rgba(0,0,0,0.15);border-top-color:var(--accent);"></span>
            </div>`;
        document.getElementById('configura-titolo').textContent = nome || 'Configura Prodotto';
        document.getElementById('btn-salva-config-label').textContent = 'Salva Configurazione';
        document.getElementById('configura-fascia-prezzo').textContent = '';

        this.switchSection('configura');

        try {
            const res = await api.fetchJson(`api/cliente/configurazioni?codice=${codice}`);
            const albero = res.data || res;
            this.stato.alberoCorrente = albero;

            // Mostra fascia prezzo se disponibile
            if (albero.prezzoMin != null && albero.prezzoMax != null) {
                document.getElementById('configura-fascia-prezzo').textContent =
                    `Fascia consentita: €${parseFloat(albero.prezzoMin).toFixed(2)} – €${parseFloat(albero.prezzoMax).toFixed(2)}`;
            }

            const container = document.getElementById('albero-container');
            container.innerHTML = '';
            container.appendChild(this.buildNodoConfigura(albero));

        } catch (err) {
            document.getElementById('albero-container').innerHTML =
                `<div class="field-error">Errore caricamento albero: ${this.escapeHtml(err.message)}</div>`;
        }
    },

    /**
     * Costruisce il nodo DOM per la sezione configura.
     * - Nodo SEMPLICE: select con le SKU disponibili.
     * - Nodo COMPOSTO: label + pulsante "Espandi" (i figli sono già inclusi nell'albero ma
     *   vengono mostrati solo dopo il click, simulando il lazy load visivo).
     * @param {Object} nodo - Il nodo dell'albero.
     * @returns {HTMLElement}
     */
    buildNodoConfigura: function (nodo) {
        if (nodo.tipo === 'SEMPLICE') {
            const tpl = document.getElementById('tpl-config-nodo-semplice').content.cloneNode(true);
            const div = tpl.querySelector('.config-node');
            div.dataset.id = nodo.id;

            tpl.querySelector('.node-label').textContent = nodo.nome;

            const select = tpl.querySelector('.node-sku-select');
            select.name = `sku_${nodo.id}`;
            select.required = true;

            (nodo.skus || []).forEach(sku => {
                const opt = document.createElement('option');
                opt.value = sku.id;
                opt.textContent = `[${sku.codice}] ${sku.nome} — €${parseFloat(sku.prezzo).toFixed(2)}`;
                select.appendChild(opt);
            });

            return div;

        } else {
            // COMPOSTO
            const tpl = document.getElementById('tpl-config-nodo-composto').content.cloneNode(true);
            const div = tpl.querySelector('.config-node');
            div.dataset.id = nodo.id;
            // Salva i figli serializzati per espanderli al click senza chiamata aggiuntiva
            div.dataset.figli = JSON.stringify(nodo.figli || []);

            tpl.querySelector('.node-label').textContent = nodo.nome;

            return div;
        }
    },

    /**
     * Espande un nodo COMPOSTO on-demand (mostra i figli, nasconde il pulsante Espandi).
     * I figli erano già inclusi nella risposta iniziale e sono serializzati in data-figli.
     * @param {HTMLElement} nodoDiv - Il div `.config-node-composto` da espandere.
     */
    espandiNodo: function (nodoDiv) {
        const btnEspandi = nodoDiv.querySelector('.btn-espandi');
        if (btnEspandi) btnEspandi.style.display = 'none';

        const figli = JSON.parse(nodoDiv.dataset.figli || '[]');
        const childrenContainer = nodoDiv.querySelector('.node-children');
        childrenContainer.innerHTML = '';

        figli.forEach(figlio => {
            childrenContainer.appendChild(this.buildNodoConfigura(figlio));
        });
    },

    /**
     * Raccoglie le scelte SKU dal form (un oggetto {idProdotto: idSku} per ogni nodo semplice).
     * Controlla anche che non ci siano nodi composti ancora non espansi.
     * @returns {{valido: boolean, scelte: Object, errore: string|null}}
     */
    raccogliScelte: function () {
        // Nodi composti non ancora espansi = pulsante "Espandi" ancora visibile
        const nonEspansi = [...document.querySelectorAll('#albero-container .btn-espandi')]
            .filter(btn => btn.style.display !== 'none');
        if (nonEspansi.length > 0) {
            return { valido: false, scelte: {}, errore: 'Espandi tutti i componenti prima di salvare.' };
        }

        const scelte = {};
        let errore = null;
        document.querySelectorAll('#albero-container select[name^="sku_"]').forEach(sel => {
            const idProdotto = sel.name.replace('sku_', '');
            if (!sel.value) {
                errore = 'Scegli una SKU per ogni componente semplice prima di salvare.';
            } else {
                scelte[idProdotto] = parseInt(sel.value);
            }
        });

        return { valido: errore === null, scelte, errore };
    },

    /**
     * Gestisce il submit del form di salvataggio/aggiornamento di una configurazione.
     * Raccoglie le scelte dell'utente, convalida i dati e invia la richiesta al server.
     * @param {Event} e - L'evento di submit del form.
     */
    handleSalvaConfigurazione: async function (e) {
        e.preventDefault();

        const nome = document.getElementById('input-nome-config').value.trim();
        const errorNome = document.getElementById('error-nome-config');
        const errorGenerico = document.getElementById('configura-errore');

        errorNome.style.display = 'none';
        errorGenerico.style.display = 'none';

        if (!nome) {
            errorNome.style.display = 'block';
            return;
        }

        const { valido, scelte, errore } = this.raccogliScelte();
        if (!valido) {
            errorGenerico.textContent = errore;
            errorGenerico.style.display = 'block';
            return;
        }

        const albero = this.stato.alberoCorrente;
        if (!albero) return;

        const payload = {
            nome,
            codiceRadice: albero.codice,
            scelte
        };

        const btnSalva = document.getElementById('btn-salva-config');
        btnSalva.disabled = true;

        try {
            let idConfigToShow;
            if (this.stato.modalitaForm === 'modifica' && this.stato.idConfigInModifica != null) {
                await api.fetchJson(`api/cliente/configurazioni/${this.stato.idConfigInModifica}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
                idConfigToShow = this.stato.idConfigInModifica;
                this.mostraMessaggio('Configurazione aggiornata con successo!', 'success');
            } else {
                const res = await api.fetchJson('api/cliente/configurazioni', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                idConfigToShow = res.id;
                this.mostraMessaggio('Configurazione salvata con successo!', 'success');
            }

            // Vai al dettaglio della configurazione appena salvata
            this.apriDettaglio(idConfigToShow);

            // Ricarica la lista in background
            this.caricaConfigurazioni();

        } catch (err) {
            errorGenerico.textContent = err.message;
            errorGenerico.style.display = 'block';
        } finally {
            btnSalva.disabled = false;
        }
    },

    // -------------------------------------------------------------------------
    // Sezione 3: Dettaglio configurazione (read-only)
    // -------------------------------------------------------------------------

    /**
     * Carica e visualizza i dettagli congelati di una configurazione specifica salvata.
     * @param {number|string} id - L'ID della configurazione da mostrare.
     */
    apriDettaglio: async function (id) {
        this.switchSection('dettaglio');
        document.getElementById('dettaglio-nome').textContent = 'Caricamento...';
        document.getElementById('dettaglio-prezzo').textContent = '';
        document.getElementById('dettaglio-albero-container').innerHTML = `
            <div class="empty-state">
                <span class="spinner" style="border-color:rgba(0,0,0,0.15);border-top-color:var(--accent);"></span>
            </div>`;

        try {
            const res = await api.fetchJson(`api/cliente/configurazioni/${id}`);
            const { configurazione, albero, voci } = res;

            document.getElementById('dettaglio-nome').textContent = configurazione.nome;
            document.getElementById('dettaglio-prezzo').textContent =
                `Prezzo totale: €${parseFloat(configurazione.prezzoTotale).toFixed(2)}`;

            const container = document.getElementById('dettaglio-albero-container');
            container.innerHTML = '';
            container.appendChild(this.buildNodoDettaglio(albero, voci));

        } catch (err) {
            document.getElementById('dettaglio-albero-container').innerHTML =
                `<div class="field-error">Errore: ${this.escapeHtml(err.message)}</div>`;
        }
    },

    /**
     * Costruisce ricorsivamente il DOM del dettaglio (read-only).
     * - Nodo SEMPLICE: mostra la card SKU scelta (foto, nome, desc, prezzo congelato).
     * - Nodo COMPOSTO: intestazione + ul figli ricorsivi.
     * @param {Object} nodo
     * @param {Object} voci - Map idProdotto → VoceConfigurazioneDTO
     * @returns {HTMLElement}
     */
    buildNodoDettaglio: function (nodo, voci, livello = 0) {
        const wrapper = document.createElement('div');
        wrapper.className = 'dettaglio-nodo';
        wrapper.dataset.livello = livello;

        if (nodo.tipo === 'COMPOSTO') {
            // Intestazione: badge + nome
            const header = document.createElement('div');
            header.className = 'dettaglio-nodo-header dettaglio-header-composto';
            header.innerHTML = `<span class="badge badge-composto">COMPOSTO</span>
                                <span class="dettaglio-nodo-nome">${this.escapeHtml(nodo.nome)}</span>`;
            wrapper.appendChild(header);

            // Figli ricorsivi
            const childrenWrap = document.createElement('div');
            childrenWrap.className = 'dettaglio-children';
            (nodo.figli || []).forEach(figlio => {
                childrenWrap.appendChild(this.buildNodoDettaglio(figlio, voci, livello + 1));
            });
            wrapper.appendChild(childrenWrap);

        } else if (nodo.tipo === 'SEMPLICE') {
            // Intestazione: badge + nome
            const header = document.createElement('div');
            header.className = 'dettaglio-nodo-header dettaglio-header-semplice';
            header.innerHTML = `<span class="badge badge-semplice">SEMPLICE</span>
                                <span class="dettaglio-nodo-nome">${this.escapeHtml(nodo.nome)}</span>`;
            wrapper.appendChild(header);

            // Card SKU scelta
            const voce = voci[String(nodo.id)];
            if (voce) {
                const sku = voce.sku || {};
                const tpl = document.getElementById('tpl-dettaglio-sku').content.cloneNode(true);
                const card = tpl.querySelector('.sku-detail-card');

                const img = tpl.querySelector('.sku-foto');
                if (sku.fotografia) {
                    img.src = '/' + sku.fotografia;
                    img.style.display = 'block';
                }

                // Titolo: codice + ' – ' + nome
                const nomeEl = tpl.querySelector('.sku-nome');
                nomeEl.textContent = (sku.codice ? sku.codice + ' – ' : '') + (sku.nome || '—');

                tpl.querySelector('.sku-desc').textContent = sku.descrizioneTecnica || '';

                tpl.querySelector('.sku-prezzo').textContent =
                    `${parseFloat(voce.prezzoCongelato || 0).toFixed(2)} € (prezzo al momento della configurazione)`;

                wrapper.appendChild(card);
            }
        }

        return wrapper;
    },


    // -------------------------------------------------------------------------
    // Sezione 4: Le mie Configurazioni
    // -------------------------------------------------------------------------

    /**
     * Recupera l'elenco delle configurazioni salvate dal cliente e le visualizza
     * in una tabella ordinata con i pulsanti per visualizzare, modificare, clonare o eliminare.
     */
    caricaConfigurazioni: async function () {
        const container = document.getElementById('configurazioni-container');
        container.innerHTML = `
            <div class="empty-state">
                <span class="spinner" style="border-color:rgba(0,0,0,0.15);border-top-color:var(--accent);"></span>
                <p style="margin-top:0.75rem;">Caricamento...</p>
            </div>`;

        try {
            const res = await api.fetchJson('api/cliente/configurazioni');
            const lista = res.data || [];

            if (lista.length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <i class="fa-solid fa-list-check empty-icon"></i>
                        <p>Non hai ancora salvato nessuna configurazione.</p>
                    </div>`;
                return;
            }

            container.innerHTML = '';
            lista.forEach(conf => {
                const tpl = document.getElementById('tpl-config-row').content.cloneNode(true);
                const row = tpl.querySelector('.search-result-item');
                row.dataset.id = conf.id;

                tpl.querySelector('.conf-nome').textContent = conf.nome;
                const data = conf.dataCreazione
                    ? new Date(conf.dataCreazione).toLocaleDateString('it-IT')
                    : '';
                tpl.querySelector('.conf-meta').textContent = data ? `Creata il ${data}` : '';
                tpl.querySelector('.conf-prezzo').textContent =
                    `€${parseFloat(conf.prezzoTotale).toFixed(2)}`;

                container.appendChild(tpl);
            });

        } catch (err) {
            container.innerHTML =
                `<div class="field-error" style="padding:1rem;">Errore: ${this.escapeHtml(err.message)}</div>`;
        }
    },

    /**
     * Apre la sezione configura pre-compilata con i dati di una configurazione esistente.
     * @param {number} id
     */
    apriModifica: async function (id) {
        try {
            const res = await api.fetchJson(`api/cliente/configurazioni/${id}`);
            const { configurazione, albero, voci } = res;

            this.stato.modalitaForm = 'modifica';
            this.stato.idConfigInModifica = id;
            this.stato.alberoCorrente = albero;

            document.getElementById('form-configura').reset();
            document.getElementById('configura-errore').style.display = 'none';
            document.getElementById('error-nome-config').style.display = 'none';
            document.getElementById('input-nome-config').value = configurazione.nome;
            document.getElementById('configura-titolo').textContent = 'Modifica: ' + albero.nome;
            document.getElementById('btn-salva-config-label').textContent = 'Aggiorna Configurazione';

            if (albero.prezzoMin != null && albero.prezzoMax != null) {
                document.getElementById('configura-fascia-prezzo').textContent =
                    `Fascia consentita: €${parseFloat(albero.prezzoMin).toFixed(2)} – €${parseFloat(albero.prezzoMax).toFixed(2)}`;
            } else {
                document.getElementById('configura-fascia-prezzo').textContent = '';
            }

            const container = document.getElementById('albero-container');
            container.innerHTML = '';
            // Espandi l'intero albero subito e pre-seleziona le SKU precedenti
            this.buildNodoModifica(albero, voci, container);

            this.switchSection('configura');

        } catch (err) {
            this.mostraMessaggio('Errore caricamento configurazione: ' + err.message, 'error');
        }
    },

    /**
     * Variante di buildNodoConfigura che espande tutto l'albero e pre-seleziona le SKU dalla voce.
     * @param {Object} nodo
     * @param {Object} voci - Map idProdotto → VoceConfigurazioneDTO
     * @param {HTMLElement} parent - Contenitore in cui appendere il nodo.
     */
    buildNodoModifica: function (nodo, voci, parent) {
        if (nodo.tipo === 'SEMPLICE') {
            const tpl = document.getElementById('tpl-config-nodo-semplice').content.cloneNode(true);
            const div = tpl.querySelector('.config-node');
            div.dataset.id = nodo.id;
            tpl.querySelector('.node-label').textContent = nodo.nome;

            const select = tpl.querySelector('.node-sku-select');
            select.name = `sku_${nodo.id}`;
            select.required = true;

            // VoceConfigurazioneDTO → { sku: SKU, prezzoCongelato }; chiavi JSON sempre stringhe
            const voce = voci[String(nodo.id)];
            const skuSelezionataId = voce ? voce.sku.id : null;

            (nodo.skus || []).forEach(sku => {
                const opt = document.createElement('option');
                opt.value = sku.id;
                opt.textContent = `[${sku.codice}] ${sku.nome} — €${parseFloat(sku.prezzo).toFixed(2)}`;
                if (skuSelezionataId && sku.id == skuSelezionataId) opt.selected = true;
                select.appendChild(opt);
            });

            parent.appendChild(div);

        } else {
            // COMPOSTO: espandi subito (non serve il pulsante in modalità modifica)
            const tpl = document.getElementById('tpl-config-nodo-composto').content.cloneNode(true);
            const div = tpl.querySelector('.config-node');
            div.dataset.id = nodo.id;
            tpl.querySelector('.node-label').textContent = nodo.nome;

            const btnEspandi = tpl.querySelector('.btn-espandi');
            if (btnEspandi) btnEspandi.style.display = 'none';

            parent.appendChild(div);

            const childrenContainer = div.querySelector('.node-children');
            (nodo.figli || []).forEach(figlio => {
                this.buildNodoModifica(figlio, voci, childrenContainer);
            });
        }
    },

    /**
     * Clona una configurazione: effettua direttamente la copia sul backend
     * e ricarica la lista.
     * @param {number} id
     */
    cloneConfigurazione: async function (id) {
        try {
            this.mostraMessaggio('Clonazione in corso...', 'info');

            const cloneRes = await api.fetchJson(`api/cliente/configurazioni/${id}/clona`, {
                method: 'POST'
            });

            this.mostraMessaggio('Configurazione clonata con successo!', 'success');
            this.apriDettaglio(cloneRes.id);
            this.caricaConfigurazioni();

        } catch (err) {
            this.mostraMessaggio('Errore clonazione: ' + err.message, 'error');
        }
    },

    /**
     * Elimina una configurazione salvata sia sul server che dall'interfaccia utente (DOM).
     * @param {number|string} id - L'ID della configurazione da cancellare.
     * @param {HTMLElement} rowEl - L'elemento DOM della riga della tabella da rimuovere.
     */
    eliminaConfigurazione: async function (id, rowEl) {
        const confermato = await this.confermaAzione('Eliminare questa configurazione? L\'operazione è irreversibile.');
        if (!confermato) return;

        try {
            await api.fetchJson(`api/cliente/configurazioni/${id}`, { method: 'DELETE' });
            rowEl.remove();
            this.mostraMessaggio('Configurazione eliminata.', 'success');

            // Se non ci sono più righe mostra empty state
            const container = document.getElementById('configurazioni-container');
            if (container.querySelectorAll('.search-result-item').length === 0) {
                container.innerHTML = `
                    <div class="empty-state">
                        <i class="fa-solid fa-list-check empty-icon"></i>
                        <p>Non hai ancora salvato nessuna configurazione.</p>
                    </div>`;
            }
        } catch (err) {
            this.mostraMessaggio('Errore eliminazione: ' + err.message, 'error');
        }
    },

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    /**
     * Visualizza un messaggio toast temporaneo di notifica all'utente.
     * @param {string} testo - Il messaggio da visualizzare.
     * @param {string} [tipo='success'] - Il tipo di notifica ('success', 'error', 'warning', 'info').
     */
    mostraMessaggio: function (testo, tipo = 'success') {
        const container = document.getElementById('toast-container');
        if (!container) return;
        const toast = document.createElement('div');
        toast.className = `toast toast-${tipo}`;
        let icon = 'info-circle';
        if (tipo === 'success') icon = 'circle-check';
        if (tipo === 'error') icon = 'circle-xmark';
        if (tipo === 'warning') icon = 'triangle-exclamation';
        toast.innerHTML = `<i class="fa-solid fa-${icon}" style="margin-right:0.5rem;"></i>${this.escapeHtml(testo)}`;
        container.appendChild(toast);
        setTimeout(() => { if (toast.parentNode) toast.remove(); }, 4000);
    },

    /**
     * Mostra una modale di conferma personalizzata al posto di window.confirm.
     * @param {string} messaggio - Il testo da mostrare nella modale.
     * @returns {Promise<boolean>} Una Promise che si risolve con true se confermato, false altrimenti.
     */
    confermaAzione: function(messaggio) {
        return new Promise((resolve) => {
            const overlay = document.createElement('div');
            overlay.style.cssText = 'position: fixed; top: 0; left: 0; width: 100vw; height: 100vh; background: rgba(0,0,0,0.5); z-index: 9999; display: flex; align-items: center; justify-content: center; opacity: 0; transition: opacity 0.2s;';
            
            const modal = document.createElement('div');
            modal.style.cssText = 'background: var(--card-bg); padding: 1.5rem; border-radius: var(--radius-md); box-shadow: 0 10px 25px rgba(0,0,0,0.2); max-width: 400px; width: 90%; transform: translateY(-20px); transition: transform 0.2s;';
            
            modal.innerHTML = `
                <div style="display: flex; align-items: center; margin-bottom: 1rem; color: var(--text-primary); font-weight: 600;">
                    <i class="fa-solid fa-circle-exclamation" style="color: var(--warning); margin-right: 0.5rem; font-size: 1.25rem;"></i>
                    Conferma operazione
                </div>
                <p style="margin-bottom: 1.5rem; color: var(--text-secondary); font-size: 0.95rem;">${this.escapeHtml(messaggio)}</p>
                <div style="display: flex; justify-content: flex-end; gap: 0.75rem;">
                    <button class="btn btn-ghost" id="btn-modal-annulla">Annulla</button>
                    <button class="btn btn-primary" id="btn-modal-conferma" style="background: var(--warning); border-color: var(--warning);">Conferma</button>
                </div>
            `;
            
            overlay.appendChild(modal);
            document.body.appendChild(overlay);
            
            // Animazione ingresso
            requestAnimationFrame(() => {
                overlay.style.opacity = '1';
                modal.style.transform = 'translateY(0)';
            });
            
            const close = (result) => {
                overlay.style.opacity = '0';
                modal.style.transform = 'translateY(-20px)';
                setTimeout(() => overlay.remove(), 200);
                resolve(result);
            };
            
            modal.querySelector('#btn-modal-annulla').addEventListener('click', () => close(false));
            modal.querySelector('#btn-modal-conferma').addEventListener('click', () => close(true));
        });
    },

    /**
     * Effettua l'escape dei caratteri HTML per prevenire XSS quando si inserisce testo nel DOM.
     * @param {string} str - La stringa da rendere sicura.
     * @returns {string} La stringa con i caratteri speciali HTML neutralizzati.
     */
    escapeHtml: function (str) {
        if (!str) return '';
        const div = document.createElement('div');
        div.textContent = str;
        return div.innerHTML;
    }
};

document.addEventListener('DOMContentLoaded', () => {
    AppCliente.init();
});
