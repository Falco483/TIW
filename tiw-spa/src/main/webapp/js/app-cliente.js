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

    bindGlobalEvents: function () {
        // Sidebar: navigazione tra sezioni
        document.getElementById('sidebar').addEventListener('click', (e) => {
            const link = e.target.closest('a[data-section]');
            if (link) {
                e.preventDefault();
                const sez = link.dataset.section;
                this.switchSection(sez);
                if (sez === 'configurazioni') this.caricaConfigurazioni();
                document.getElementById('sidebar').classList.remove('open');
                document.getElementById('sidebarOverlay').classList.remove('visible');
            }
        });

        // Hamburger mobile
        document.getElementById('btnHamburger').addEventListener('click', () => {
            document.getElementById('sidebar').classList.add('open');
            document.getElementById('sidebarOverlay').classList.add('visible');
        });
        document.getElementById('sidebarOverlay').addEventListener('click', () => {
            document.getElementById('sidebar').classList.remove('open');
            document.getElementById('sidebarOverlay').classList.remove('visible');
        });

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

    switchSection: function (sectionId) {
        document.querySelectorAll('.sidebar-nav a').forEach(a => a.classList.remove('active'));
        document.querySelector(`a[data-section="${sectionId}"]`)?.classList.add('active');
        document.querySelectorAll('.app-section').forEach(sec => sec.classList.remove('active'));
        document.getElementById(`section-${sectionId}`).classList.add('active');
        this.stato.sezioneAttiva = sectionId;
    },

    // -------------------------------------------------------------------------
    // Sezione 1: Catalogo
    // -------------------------------------------------------------------------

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

            container.innerHTML = '';
            lista.forEach(p => {
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

        } catch (err) {
            container.innerHTML = `<div class="field-error" style="padding:1rem;">Errore: ${this.escapeHtml(err.message)}</div>`;
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
            if (this.stato.modalitaForm === 'modifica' && this.stato.idConfigInModifica != null) {
                await api.fetchJson(`api/cliente/configurazioni/${this.stato.idConfigInModifica}`, {
                    method: 'PUT',
                    body: JSON.stringify(payload)
                });
                this.mostraMessaggio('Configurazione aggiornata con successo!', 'success');
            } else {
                await api.fetchJson('api/cliente/configurazioni', {
                    method: 'POST',
                    body: JSON.stringify(payload)
                });
                this.mostraMessaggio('Configurazione salvata con successo!', 'success');
            }

            // Vai alla lista configurazioni e ricarica
            this.switchSection('configurazioni');
            await this.caricaConfigurazioni();

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
    buildNodoDettaglio: function (nodo, voci) {
        const wrapper = document.createElement('div');
        wrapper.style.cssText = 'margin: 0.4rem 0 0.4rem 1.25rem; padding-left: 0.75rem; border-left: 2px solid var(--card-border);';

        const label = document.createElement('div');
        label.style.cssText = 'font-weight: 600; font-size: 0.875rem; margin-bottom: 0.3rem;';
        label.textContent = nodo.nome;
        wrapper.appendChild(label);

        if (nodo.tipo === 'SEMPLICE') {
            // voci ha chiavi stringa (JSON object keys sono sempre stringhe)
            const voce = voci[String(nodo.id)];
            if (voce) {
                // VoceConfigurazioneDTO → { sku: SKU, prezzoCongelato: BigDecimal }
                const sku = voce.sku || {};
                const tpl = document.getElementById('tpl-dettaglio-sku').content.cloneNode(true);
                tpl.querySelector('.sku-nome').textContent = sku.nome || '—';
                tpl.querySelector('.sku-desc').textContent = sku.descrizioneTecnica || '';
                tpl.querySelector('.sku-prezzo').textContent =
                    `€${parseFloat(voce.prezzoCongelato || 0).toFixed(2)} (prezzo al momento della configurazione)`;

                const img = tpl.querySelector('.sku-foto');
                if (sku.fotografia) {
                    img.src = sku.fotografia.startsWith('uploads/') ? sku.fotografia : 'uploads/' + sku.fotografia;
                    img.style.display = 'block';
                }

                wrapper.appendChild(tpl.querySelector('.sku-detail-card'));
            }

        } else if (nodo.tipo === 'COMPOSTO') {
            (nodo.figli || []).forEach(figlio => {
                wrapper.appendChild(this.buildNodoDettaglio(figlio, voci));
            });
        }

        return wrapper;
    },

    // -------------------------------------------------------------------------
    // Sezione 4: Le mie Configurazioni
    // -------------------------------------------------------------------------

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
     * Clona una configurazione: carica i dati e apre il form di creazione pre-compilato.
     * Il nome viene prefissato con "Copia di " per distinguerla dall'originale.
     * @param {number} id
     */
    cloneConfigurazione: async function (id) {
        try {
            const res = await api.fetchJson(`api/cliente/configurazioni/${id}`);
            const { configurazione, albero, voci } = res;

            this.stato.modalitaForm = 'crea';
            this.stato.idConfigInModifica = null;
            this.stato.alberoCorrente = albero;

            document.getElementById('form-configura').reset();
            document.getElementById('configura-errore').style.display = 'none';
            document.getElementById('error-nome-config').style.display = 'none';
            document.getElementById('input-nome-config').value = 'Copia di ' + configurazione.nome;
            document.getElementById('configura-titolo').textContent = albero.nome;
            document.getElementById('btn-salva-config-label').textContent = 'Salva Configurazione';

            if (albero.prezzoMin != null && albero.prezzoMax != null) {
                document.getElementById('configura-fascia-prezzo').textContent =
                    `Fascia consentita: €${parseFloat(albero.prezzoMin).toFixed(2)} – €${parseFloat(albero.prezzoMax).toFixed(2)}`;
            } else {
                document.getElementById('configura-fascia-prezzo').textContent = '';
            }

            const container = document.getElementById('albero-container');
            container.innerHTML = '';
            this.buildNodoModifica(albero, voci, container);

            this.switchSection('configura');
            this.mostraMessaggio('Configurazione clonata. Modifica e salva per creare la copia.', 'info');

        } catch (err) {
            this.mostraMessaggio('Errore clonazione: ' + err.message, 'error');
        }
    },

    eliminaConfigurazione: async function (id, rowEl) {
        if (!confirm('Eliminare questa configurazione? L\'operazione è irreversibile.')) return;

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
