"""
genera_slide.py
Genera un file PowerPoint con le tabelle "Controller/Event Handler"
ed "Eventi & Azioni" per i moduli SPA e SSR del progetto TIW.

Uso:  python genera_slide.py
Output: documentation/tabelle_tiw.pptx
"""

from pptx import Presentation
from pptx.util import Inches, Pt, Emu
from pptx.dml.color import RGBColor
from pptx.enum.text import PP_ALIGN
import os

# ---------------------------------------------------------------------------
# Palette colori (stile slide del corso)
# ---------------------------------------------------------------------------
BLU_HEADER   = RGBColor(0x26, 0x69, 0xAC)   # blu intestazione principale
BLU_SUBHDR   = RGBColor(0x41, 0x72, 0xC4)   # blu intestazione secondaria
BLU_CHIARO   = RGBColor(0xD6, 0xE4, 0xF7)   # righe alternate
BIANCO       = RGBColor(0xFF, 0xFF, 0xFF)
NERO         = RGBColor(0x00, 0x00, 0x00)
GRIGIO_TESTO = RGBColor(0x40, 0x40, 0x40)
ARANCIO      = RGBColor(0xC0, 0x50, 0x00)   # testo in evidenza

# ---------------------------------------------------------------------------
# Dati delle tabelle
# ---------------------------------------------------------------------------

# Intestazioni colonne
HDR_CTRL   = ("Evento", "Controllore")
HDR_AZIONI = ("Evento", "Azione")

# ── SPA Fornitore ─────────────────────────────────────────────────────────

SPA_F_CTRL = [
    ("index → login form → submit",
     "api.fetchJson (POST)",
     "POST username, password",
     "LoginServlet (servlet)"),
    ("Home page → load",
     "AppFornitore.init (makeCall)",
     "GET (nessun parametro)",
     "HomeFornitoreServlet (servlet)"),
    ("Form SKU → submit",
     "AppFornitore.handleSubmitSku (makeCall)",
     "POST dati SKU + foto",
     "ApiSkuController (servlet)"),
    ("Form prodotto semplice → submit",
     "AppFornitore.handleSubmitSemplice (makeCall)",
     "POST JSON (codice, nome, SKU)",
     "ApiProdottoTreeController (servlet)"),
    ("Form prodotto composto → submit",
     "AppFornitore.handleCreaComposto (makeCall)",
     "POST JSON (codice, nome, prezzi, figli)",
     "ApiProdottoTreeController (servlet)"),
    ("Tree editor → click Aggiungi figlio",
     "AppFornitore.handleTreeAddChild",
     "—", "—"),
    ("Tree editor → click Aggiungi SKU",
     "AppFornitore.handleTreeAddSku",
     "—", "—"),
    ("Tree editor → click Scollega / Elimina nodo",
     "AppFornitore.handleTreeUnlink / handleTreeDelete",
     "—", "—"),
    ("Tree editor → focusout campo editabile",
     "AppFornitore.handleTreeInlineEdit",
     "—", "—"),
    ("Tree editor → click Salva",
     "AppFornitore.handleSalvaTree (makeCall)",
     "POST JSON (azioni pendenti)",
     "ApiSyncController (servlet)"),
    ("Ricerca → form submit",
     "AppFornitore.handleSearch (makeCall)",
     "GET ?q=…",
     "ApiRicercaController (servlet)"),
    ("Risultati → click su item",
     "AppFornitore.handleExpandSearchResult (makeCall)",
     "GET ?id=…",
     "ApiProdottoController (servlet)"),
    ("SKU → click Elimina",
     "AppFornitore.handleEliminaSku (makeCall)",
     "DELETE ?id=…",
     "ApiSkuController (servlet)"),
    ("Logout",
     "redirect window.location.href",
     "GET /logout",
     "LoginServlet (servlet)"),
]

SPA_F_AZIONI = [
    ("index → login form → submit",
     "Controllo credenziali",
     "POST username, password",
     "Controllo credenziali; creazione sessione"),
    ("Home page → load",
     "Aggiorna view con SKU e prodotti orfani",
     "GET (nessun parametro)",
     "Estrazione SKU e prodotti orfani"),
    ("Form SKU → submit",
     "Controllo dati; invio multipart",
     "POST dati SKU + foto",
     "Controllo codice univoco; upload foto; inserimento SKU"),
    ("Form prodotto semplice → submit",
     "Controllo dati; invio JSON",
     "POST JSON (codice, nome, SKU)",
     "Controllo codice univoco; inserimento prodotto + associazioni SKU"),
    ("Form prodotto composto → submit",
     "Controllo dati e vincoli prezzo; invio JSON",
     "POST JSON (codice, nome, prezzi, figli)",
     "Verifica vincoli prezzo e profondità ≤ 3; inserimento prodotto + figli"),
    ("Tree editor → Aggiungi figlio / SKU",
     "Aggiunta nodo/SKU al DOM; accodamento azione",
     "—", "—"),
    ("Tree editor → Scollega / Elimina",
     "Rimozione dal DOM; accodamento azione",
     "—", "—"),
    ("Tree editor → inline edit focusout",
     "Aggiornamento valore nel DOM; accodamento UPDATE",
     "—", "—"),
    ("Tree editor → click Salva",
     "Invio batch azioni pendenti",
     "POST JSON (azioni pendenti)",
     "Esecuzione transazionale delle azioni; aggiornamento DB"),
    ("Ricerca → form submit",
     "Aggiorna view con risultati",
     "GET ?q=…",
     "Ricerca full-text su SKU e prodotti"),
    ("Risultati → click su item",
     "Aggiorna view con albero / dettaglio SKU",
     "GET ?id=…",
     "Estrazione albero prodotto o dati SKU"),
    ("SKU → click Elimina",
     "Rimozione dalla view",
     "DELETE ?id=…",
     "Eliminazione fisica SKU e configurazioni associate"),
    ("Logout",
     "Cancellazione stato locale; redirect login",
     "GET /logout",
     "Invalidazione sessione"),
]

# ── SPA Cliente ────────────────────────────────────────────────────────────

SPA_C_CTRL = [
    ("index → login form → submit",
     "api.fetchJson (POST)",
     "POST username, password",
     "LoginServlet (servlet)"),
    ("Home page → load",
     "AppCliente.init → caricaCatalogo (makeCall)",
     "GET (nessun parametro)",
     "HomeClienteServlet (servlet)"),
    ("Catalogo → click prodotto",
     "AppCliente.apriConfigurazione (makeCall)",
     "GET ?codice=…",
     "ApiConfigurazioneController (servlet)"),
    ("Configura → click Espandi nodo",
     "AppCliente.espandiNodo",
     "—", "—"),
    ("Configura → form submit (nuova)",
     "AppCliente.handleSalvaConfigurazione (makeCall)",
     "POST JSON (nome, codiceRadice, scelte)",
     "ApiConfigurazioneController (servlet)"),
    ("Configura → form submit (modifica)",
     "AppCliente.handleSalvaConfigurazione (makeCall)",
     "PUT JSON (nome, scelte)",
     "ApiConfigurazioneController (servlet)"),
    ("Le mie configurazioni → load",
     "AppCliente.caricaConfigurazioni (makeCall)",
     "GET /api/cliente/configurazioni",
     "ApiConfigurazioneController (servlet)"),
    ("Configurazione → click Dettaglio",
     "AppCliente.apriDettaglio (makeCall)",
     "GET /api/cliente/configurazioni/{id}",
     "ApiConfigurazioneController (servlet)"),
    ("Configurazione → click Modifica",
     "AppCliente.apriModifica (makeCall)",
     "GET /api/cliente/configurazioni/{id}",
     "ApiConfigurazioneController (servlet)"),
    ("Configurazione → click Clona",
     "AppCliente.cloneConfigurazione (makeCall)",
     "POST JSON (payload clonato)",
     "ApiConfigurazioneController (servlet)"),
    ("Configurazione → click Elimina",
     "AppCliente.eliminaConfigurazione (makeCall)",
     "DELETE /api/cliente/configurazioni/{id}",
     "ApiConfigurazioneController (servlet)"),
    ("Logout",
     "redirect window.location.href",
     "GET /logout",
     "LoginServlet (servlet)"),
]

SPA_C_AZIONI = [
    ("index → login form → submit",
     "Controllo credenziali",
     "POST username, password",
     "Controllo credenziali; creazione sessione"),
    ("Home page → load",
     "Aggiorna view con catalogo prodotti (Z-A, paginato)",
     "GET (nessun parametro)",
     "Estrazione prodotti composti radice"),
    ("Catalogo → click prodotto",
     "Aggiorna view con albero configurazione",
     "GET ?codice=…",
     "Estrazione albero prodotto"),
    ("Configura → click Espandi nodo",
     "Aggiorna view con figli del nodo (dati in RAM)",
     "—", "—"),
    ("Configura → form submit (nuova)",
     "Controllo dati; aggiorna view con dettaglio",
     "POST JSON (nome, codiceRadice, scelte SKU)",
     "Controllo dati; price snapshotting; inserimento configurazione"),
    ("Configura → form submit (modifica)",
     "Controllo dati; aggiorna view con dettaglio",
     "PUT JSON (nome, scelte SKU)",
     "Controllo dati; price snapshotting; aggiornamento configurazione"),
    ("Le mie configurazioni → load",
     "Aggiorna view con elenco configurazioni",
     "GET /api/cliente/configurazioni",
     "Estrazione configurazioni del cliente"),
    ("Configurazione → click Dettaglio",
     "Aggiorna view con dettaglio e prezzi congelati",
     "GET /api/cliente/configurazioni/{id}",
     "Estrazione dettaglio + prezzi congelati"),
    ("Configurazione → click Modifica",
     "Aggiorna view con form pre-compilato",
     "GET /api/cliente/configurazioni/{id}",
     "Estrazione configurazione + albero + scelte"),
    ("Configurazione → click Clona",
     "Aggiunge nuova voce all'elenco",
     "POST JSON (payload clonato)",
     "Inserimento copia configurazione"),
    ("Configurazione → click Elimina",
     "Rimozione dalla view",
     "DELETE /api/cliente/configurazioni/{id}",
     "Eliminazione configurazione"),
    ("Logout",
     "Cancellazione stato locale; redirect login",
     "GET /logout",
     "Invalidazione sessione"),
]

# ── SSR Fornitore ──────────────────────────────────────────────────────────

SSR_F_CTRL = [
    ("index → login form → submit",
     '<form action="/login">',
     "POST username, password",
     "LoginServlet (servlet)"),
    ("Home page → load",
     '<a href="/fornitore/home">',
     "GET (nessun parametro)",
     "HomeFornitoreServlet (servlet)"),
    ("Form SKU → submit",
     '<form> tipoForm=sku',
     "POST (dati SKU + foto)",
     "HomeFornitoreServlet.handleCreaSku"),
    ("Form prodotto semplice → submit",
     '<form> tipoForm=semplice',
     "POST (codice, nome, IDs SKU)",
     "HomeFornitoreServlet.handleCreaSemplice"),
    ("Form prodotto composto → Ricalcola",
     '<form> azione=ricalcola',
     "POST azione=ricalcola",
     "HomeFornitoreServlet.handleRicalcolaComposto"),
    ("Form prodotto composto → submit",
     '<form> tipoForm=composto',
     "POST (dati composto + IDs figli)",
     "HomeFornitoreServlet.handleCreaComposto"),
    ("Risultato → click Elimina",
     '<form action="/fornitore/azione"> azione=ELIMINA',
     "POST (tipo, ID oggetto)",
     "AzioneCatalogoServlet (servlet)"),
    ("Risultato → click Rimuovi",
     '<form action="/fornitore/azione"> azione=RIMUOVI',
     "POST (tipo, ID oggetto, ID padre)",
     "AzioneCatalogoServlet (servlet)"),
    ("Ricerca → form submit",
     '<form action="/fornitore/cerca">',
     "GET ?keyword=…",
     "CercaCatalogoServlet (servlet)"),
    ("Risultato ricerca → load",
     "redirect interno",
     "GET (nessun parametro)",
     "RisultatoFornitoreServlet (servlet)"),
    ("Logout",
     '<form action="/logout">',
     "POST",
     "LogoutServlet (servlet)"),
]

SSR_F_AZIONI = [
    ("index → login form → submit",
     "Controllo credenziali",
     "POST username, password",
     "Controllo credenziali; creazione sessione; redirect home"),
    ("Home page → load",
     "Aggiorna view con SKU e prodotti orfani",
     "GET (nessun parametro)",
     "Estrazione SKU e prodotti orfani"),
    ("Form SKU → submit",
     "Validazione dati form",
     "POST (dati SKU + foto)",
     "Controllo codice univoco; upload foto; inserimento SKU"),
    ("Form prodotto semplice → submit",
     "Validazione dati form",
     "POST (codice, nome, IDs SKU)",
     "Calcolo prezzoMin/Max da SKU; inserimento prodotto + associazioni"),
    ("Form prodotto composto → Ricalcola",
     "Nessuna azione (semplice submit)",
     "POST azione=ricalcola",
     "Calcolo somma prezzoMin/Max figli; salvataggio in sessione"),
    ("Form prodotto composto → submit",
     "Validazione dati form",
     "POST (dati composto + IDs figli)",
     "Verifica vincoli prezzo e profondità ≤ 3; inserimento transazionale"),
    ("Risultato → click Elimina",
     "Nessuna azione client",
     "POST azione=ELIMINA (tipo, ID oggetto)",
     "Rimozione configurazioni clienti collegate; eliminazione fisica"),
    ("Risultato → click Rimuovi",
     "Nessuna azione client",
     "POST azione=RIMUOVI (tipo, ID, padre)",
     "Rimozione associazione SKU/figlio; ricalcolo prezzi padre"),
    ("Ricerca → form submit",
     "Nessuna azione client",
     "GET ?keyword=…",
     "Ricerca testuale su SKU e prodotti"),
    ("Logout",
     "Nessuna azione client",
     "POST",
     "Invalidazione sessione; redirect login"),
]

# ── SSR Cliente ────────────────────────────────────────────────────────────

SSR_C_CTRL = [
    ("index → login form → submit",
     '<form action="/login">',
     "POST username, password",
     "LoginServlet (servlet)"),
    ("Home page (catalogo) → load",
     '<a href="/cliente/home">',
     "GET (nessun parametro)",
     "HomeClienteServlet (servlet)"),
    ("Catalogo → click prodotto",
     '<a href="/cliente/configura?codice=…">',
     "GET ?codice=…",
     "ConfiguraServlet (servlet)"),
    ("Configura → click Espandi nodo",
     '<form> espandi=<id>',
     "POST espandi=<id>",
     "ConfiguraServlet.doPost → redirect GET"),
    ("Configura → form submit",
     '<form action="/cliente/configura">',
     "POST (nome, codiceRadice, sku_*)",
     "ConfiguraServlet → SalvaConfigurazioneServlet"),
    ("Le mie configurazioni → load",
     '<a href="/cliente/configurazioni">',
     "GET (nessun parametro)",
     "MieConfigurazioniServlet (servlet)"),
    ("Dettaglio configurazione → load",
     '<a href="/cliente/dettaglio?idConfig=…">',
     "GET ?idConfig=…",
     "DettaglioConfigurazioneServlet (servlet)"),
    ("Logout",
     '<form action="/logout">',
     "POST",
     "LogoutServlet (servlet)"),
]

SSR_C_AZIONI = [
    ("index → login form → submit",
     "Controllo credenziali",
     "POST username, password",
     "Controllo credenziali; creazione sessione; redirect home"),
    ("Home page (catalogo) → load",
     "Aggiorna view con prodotti radice",
     "GET (nessun parametro)",
     "Estrazione prodotti composti radice"),
    ("Catalogo → click prodotto",
     "Nessuna azione client (link)",
     "GET ?codice=…",
     "Estrazione albero prodotto; rendering form configurazione"),
    ("Configura → click Espandi nodo",
     "Invio scelte SKU correnti come hidden field",
     "POST espandi=<id> + sku_* + aperto=*",
     "Redirect GET con query string aggiornata (PRG stateless)"),
    ("Configura → form submit",
     "Validazione nome non vuoto; completezza scelte SKU",
     "POST (nome, codiceRadice, sku_*)",
     "Controllo completezza; price snapshotting; inserimento/aggiornamento configurazione"),
    ("Le mie configurazioni → load",
     "Aggiorna view con elenco configurazioni",
     "GET (nessun parametro)",
     "Estrazione configurazioni del cliente (ordinate per data)"),
    ("Dettaglio configurazione → load",
     "Aggiorna view con dettaglio e prezzi congelati",
     "GET ?idConfig=…",
     "Estrazione dettaglio configurazione + prezzi congelati"),
    ("Logout",
     "Nessuna azione client",
     "POST",
     "Invalidazione sessione; redirect login"),
]

# ── Parti comuni ───────────────────────────────────────────────────────────

COMUNI_SPA_CTRL = [
    ("index → login form → submit",
     "api.fetchJson (POST)",
     "POST username, password",
     "LoginServlet (servlet)"),
    ("Home page → load (info utente + CSRF token)",
     "AppX.init → api.getUser (makeCall)",
     "GET /api/me",
     "ApiUserController (servlet)"),
    ("Sessione scaduta (401 su qualsiasi fetch)",
     "api.fetchJson → redirect automatico",
     "—", "—"),
    ("Logout",
     "redirect window.location.href = 'login.html'",
     "GET /logout",
     "LoginServlet (servlet)"),
]

COMUNI_SPA_AZIONI = [
    ("index → login form → submit",
     "Controllo credenziali",
     "POST username, password",
     "Controllo credenziali; creazione sessione; generazione CSRF token"),
    ("Home page → load (info utente + CSRF token)",
     "Visualizza nome utente in navbar; imposta CSRF token nel meta tag",
     "GET /api/me",
     "Lettura sessione; restituzione username, nome, cognome, ruolo, CSRF token"),
    ("Sessione scaduta (401 su qualsiasi chiamata)",
     "Redirect automatico a login.html",
     "—", "—"),
    ("Logout",
     "Cancellazione stato locale; redirect a login.html",
     "GET /logout",
     "Invalidazione sessione HTTP"),
]

COMUNI_SSR_CTRL = [
    ("index → login form → submit",
     '<form action="/login" method="POST">',
     "POST username, password",
     "LoginServlet (servlet)"),
    ("Sessione assente / scaduta (qualsiasi pagina)",
     "redirect automatico (nessun controller client)",
     "— (filtri di sessione nelle servlet)",
     "redirect a /login"),
    ("Logout",
     '<form action="/logout" method="POST">',
     "POST",
     "LogoutServlet (servlet)"),
]

COMUNI_SSR_AZIONI = [
    ("index → login form → submit",
     "Controllo credenziali",
     "POST username, password",
     "Controllo credenziali; creazione sessione; CSRF token; redirect home per ruolo"),
    ("Sessione assente / scaduta (qualsiasi pagina)",
     "Nessuna azione client",
     "—",
     "Redirect a /login"),
    ("Logout",
     "Nessuna azione client",
     "POST",
     "Invalidazione sessione HTTP; redirect a /login"),
]

# ---------------------------------------------------------------------------
# Funzioni helper per formattare le celle
# ---------------------------------------------------------------------------

def set_cell_bg(cell, color):
    """Imposta il colore di sfondo di una cella."""
    from pptx.oxml.ns import qn
    from lxml import etree
    # color è un RGBColor (subclasse di int): r, g, b
    hex_str = '{:02X}{:02X}{:02X}'.format(color[0], color[1], color[2])
    tc = cell._tc
    tcPr = tc.get_or_add_tcPr()
    solidFill = etree.SubElement(tcPr, qn('a:solidFill'))
    srgbClr   = etree.SubElement(solidFill, qn('a:srgbClr'))
    srgbClr.set('val', hex_str)


def set_cell_text(cell, text, bold=False, font_size=Pt(9),
                  color=NERO, align=PP_ALIGN.LEFT):
    """Imposta testo, grassetto, dimensione e allineamento in una cella."""
    tf = cell.text_frame
    tf.word_wrap = True
    p = tf.paragraphs[0]
    p.alignment = align
    run = p.runs[0] if p.runs else p.add_run()
    run.text = text
    run.font.bold  = bold
    run.font.size  = font_size
    run.font.color.rgb = color


def merge_h(table, row, col_start, col_end):
    """Unisce orizzontalmente le celle da col_start a col_end nella stessa riga."""
    table.cell(row, col_start).merge(table.cell(row, col_end))


# ---------------------------------------------------------------------------
# Crea una slide con titolo + tabella
# ---------------------------------------------------------------------------

def add_table_slide(prs, title, subtitle, col2_label, data, note=None):
    """
    Aggiunge una slide con:
      - titolo grande (title)
      - sottotitolo opzionale in rosso (note) sulla destra del titolo
      - etichetta di gruppo (subtitle) sotto il titolo
      - tabella a 4 colonne con doppio header (Client side | Server side)
    """
    slide = prs.slides.add_slide(prs.slide_layouts[6])  # layout vuoto
    W = prs.slide_width
    H = prs.slide_height

    # ── Titolo ────────────────────────────────────────────────────────────
    title_box = slide.shapes.add_textbox(
        Inches(0.25), Inches(0.08), Inches(9), Inches(0.65))
    tf = title_box.text_frame
    tf.word_wrap = False
    p = tf.paragraphs[0]
    run = p.add_run()
    run.text = title
    run.font.size  = Pt(28)
    run.font.bold  = True
    run.font.color.rgb = NERO

    # Nota rossa (facoltativa) a destra del titolo
    if note:
        note_box = slide.shapes.add_textbox(
            Inches(9.0), Inches(0.08), Inches(4.1), Inches(0.65))
        ntf = note_box.text_frame
        ntf.word_wrap = True
        np_ = ntf.paragraphs[0]
        nr  = np_.add_run()
        nr.text = note
        nr.font.size  = Pt(10)
        nr.font.color.rgb = ARANCIO

    # ── Sottotitolo (ruolo / modulo) ──────────────────────────────────────
    sub_box = slide.shapes.add_textbox(
        Inches(0.25), Inches(0.72), Inches(12.8), Inches(0.32))
    stf = sub_box.text_frame
    sp  = stf.paragraphs[0]
    sr  = sp.add_run()
    sr.text = subtitle
    sr.font.size  = Pt(13)
    sr.font.bold  = True
    sr.font.color.rgb = BLU_HEADER

    # ── Tabella ───────────────────────────────────────────────────────────
    n_data_rows = len(data)
    n_rows = 2 + n_data_rows   # riga 0: Client/Server | riga 1: Evento/Col | dati

    left   = Inches(0.25)
    top    = Inches(1.08)
    width  = Inches(12.83)
    height = Inches(6.25)

    tbl_shape = slide.shapes.add_table(n_rows, 4, left, top, width, height)
    tbl = tbl_shape.table

    # larghezze colonne  (tot ≈ 12.83 inch)
    tbl.columns[0].width = Inches(3.40)   # evento client
    tbl.columns[1].width = Inches(2.85)   # ctrl/azione client
    tbl.columns[2].width = Inches(3.00)   # evento server
    tbl.columns[3].width = Inches(3.58)   # ctrl/azione server

    # ── Riga 0: "Client side" | "Server side" ─────────────────────────────
    merge_h(tbl, 0, 0, 1)
    merge_h(tbl, 0, 2, 3)
    for ci, label in [(0, "Client side"), (2, "Server side")]:
        cell = tbl.cell(0, ci)
        set_cell_bg(cell, BLU_HEADER)
        set_cell_text(cell, label,
                      bold=True, font_size=Pt(12),
                      color=BIANCO, align=PP_ALIGN.CENTER)

    # ── Riga 1: intestazioni colonne ──────────────────────────────────────
    for ci, label in enumerate(["Evento", col2_label, "Evento", col2_label]):
        cell = tbl.cell(1, ci)
        set_cell_bg(cell, BLU_SUBHDR)
        set_cell_text(cell, label,
                      bold=True, font_size=Pt(10),
                      color=BIANCO, align=PP_ALIGN.CENTER)

    # ── Righe dati ────────────────────────────────────────────────────────
    for ri, row_data in enumerate(data):
        bg = BLU_CHIARO if ri % 2 == 0 else BIANCO
        for ci, text in enumerate(row_data):
            cell = tbl.cell(ri + 2, ci)
            set_cell_bg(cell, bg)
            set_cell_text(cell, text, font_size=Pt(8.5))

    return slide


# ---------------------------------------------------------------------------
# Slide di separazione / copertina sezione
# ---------------------------------------------------------------------------

def add_section_slide(prs, section_title, color=BLU_HEADER):
    slide = prs.slides.add_slide(prs.slide_layouts[6])
    box = slide.shapes.add_textbox(
        Inches(0.5), Inches(2.8), Inches(12.3), Inches(1.8))
    tf = box.text_frame
    p  = tf.paragraphs[0]
    p.alignment = PP_ALIGN.CENTER
    r = p.add_run()
    r.text = section_title
    r.font.size  = Pt(40)
    r.font.bold  = True
    r.font.color.rgb = color
    return slide


# ---------------------------------------------------------------------------
# Main
# ---------------------------------------------------------------------------

def main():
    prs = Presentation()
    prs.slide_width  = Inches(13.33)
    prs.slide_height = Inches(7.5)

    NOTE_SPA = "makeCall indica una funzione che fa una chiamata asincrona al server"
    NOTE_SSR = ("I controlli di validità (client e server side) e di autorizzazione "
                "(server side) all'accesso sono previsti per tutti gli eventi "
                "che li richiedono e non sono riportati per brevità")

    # ════════════════════════════════════════════════════════════════════════
    # SEZIONE SPA
    # ════════════════════════════════════════════════════════════════════════
    add_section_slide(prs, "Modulo SPA")

    # SPA — Controller / Event Handler — Fornitore
    add_table_slide(prs,
        title    = "Controller / Event Handler",
        subtitle = "SPA  ·  Fornitore",
        col2_label = "Controllore",
        data     = SPA_F_CTRL,
        note     = NOTE_SPA)

    # SPA — Controller / Event Handler — Cliente
    add_table_slide(prs,
        title    = "Controller / Event Handler",
        subtitle = "SPA  ·  Cliente",
        col2_label = "Controllore",
        data     = SPA_C_CTRL,
        note     = NOTE_SPA)

    # SPA — Eventi & Azioni — Fornitore
    add_table_slide(prs,
        title    = "Eventi & Azioni",
        subtitle = "SPA  ·  Fornitore",
        col2_label = "Azione",
        data     = SPA_F_AZIONI,
        note     = NOTE_SSR)

    # SPA — Eventi & Azioni — Cliente
    add_table_slide(prs,
        title    = "Eventi & Azioni",
        subtitle = "SPA  ·  Cliente",
        col2_label = "Azione",
        data     = SPA_C_AZIONI,
        note     = NOTE_SSR)

    # ════════════════════════════════════════════════════════════════════════
    # SEZIONE SSR
    # ════════════════════════════════════════════════════════════════════════
    add_section_slide(prs, "Modulo SSR")

    # SSR — Controller / Event Handler — Fornitore
    add_table_slide(prs,
        title    = "Controller / Event Handler",
        subtitle = "SSR  ·  Fornitore",
        col2_label = "Controllore",
        data     = SSR_F_CTRL,
        note     = NOTE_SPA)

    # SSR — Controller / Event Handler — Cliente
    add_table_slide(prs,
        title    = "Controller / Event Handler",
        subtitle = "SSR  ·  Cliente",
        col2_label = "Controllore",
        data     = SSR_C_CTRL,
        note     = NOTE_SPA)

    # SSR — Eventi & Azioni — Fornitore
    add_table_slide(prs,
        title    = "Eventi & Azioni",
        subtitle = "SSR  ·  Fornitore",
        col2_label = "Azione",
        data     = SSR_F_AZIONI,
        note     = NOTE_SSR)

    # SSR — Eventi & Azioni — Cliente
    add_table_slide(prs,
        title    = "Eventi & Azioni",
        subtitle = "SSR  ·  Cliente",
        col2_label = "Azione",
        data     = SSR_C_AZIONI,
        note     = NOTE_SSR)

    # ════════════════════════════════════════════════════════════════════════
    # PARTI COMUNI
    # ════════════════════════════════════════════════════════════════════════
    add_section_slide(prs, "Parti comuni  Cliente & Fornitore", color=ARANCIO)

    add_table_slide(prs,
        title    = "Controller / Event Handler  —  Parti comuni",
        subtitle = "SPA  ·  Comune a Fornitore e Cliente",
        col2_label = "Controllore",
        data     = COMUNI_SPA_CTRL,
        note     = NOTE_SPA)

    add_table_slide(prs,
        title    = "Eventi & Azioni  —  Parti comuni",
        subtitle = "SPA  ·  Comune a Fornitore e Cliente",
        col2_label = "Azione",
        data     = COMUNI_SPA_AZIONI,
        note     = NOTE_SSR)

    add_table_slide(prs,
        title    = "Controller / Event Handler  —  Parti comuni",
        subtitle = "SSR  ·  Comune a Fornitore e Cliente",
        col2_label = "Controllore",
        data     = COMUNI_SSR_CTRL,
        note     = NOTE_SPA)

    add_table_slide(prs,
        title    = "Eventi & Azioni  —  Parti comuni",
        subtitle = "SSR  ·  Comune a Fornitore e Cliente",
        col2_label = "Azione",
        data     = COMUNI_SSR_AZIONI,
        note     = NOTE_SSR)

    # ────────────────────────────────────────────────────────────────────────
    output_dir  = os.path.dirname(os.path.abspath(__file__))
    output_path = os.path.join(output_dir, "tabelle_tiw.pptx")
    prs.save(output_path)
    print(f"OK  File salvato in:  {output_path}")


if __name__ == "__main__":
    main()
