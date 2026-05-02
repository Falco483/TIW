# RIASSUNTO_TEORIA — Tecnologie Informatiche per il Web (TIW)

**Documento di riferimento rapido** per identificare argomenti, tecnologie e concetti affrontati nel corso.

---

## Indice dei Capitoli

| Cap. | Titolo | Argomento Principale |
|------|--------|----------------------|
| 1 | Evoluzione delle Architetture Distribuite | Dalla preistoria informatica al Cloud Computing |
| 2 | TCP/IP | Reti e comunicazione su Internet |
| 3 | HTTP | Protocollo web, metodi, header, status code |
| 4 | HTML: Fondamenti | Markup, semantica, formulari |
| 5 | Client-Side Scripting: Introduzione | Dinamicità nel browser |
| 6 | JavaScript: Il Linguaggio | Sintassi, tipi, funzioni, scope |
| 7 | JavaScript: Le Funzioni | Closures, callback, higher-order functions |
| 8 | HTML DOM API | Manipolazione del DOM, selettori, traversal |
| 9 | Gestione degli Eventi HTML | Event handling, bubbling, delegation |
| 10 | Interazione Asincrona con XMLHttpRequest | AJAX, fetch, XHR |
| 11 | Pattern Pure HTML vs RIA: Confronto | Server-side rendering vs client-side |
| 12 | HTML5: Il Web come Piattaforma Applicativa | Canvas, Storage, Workers, Geolocation |
| 13 | Java Servlet: Architettura e Fondamenti | Request/Response, servlet lifecycle, URL mapping |
| 14 | Servlet: Sessioni, Cookie e Pattern di Comunicazione | State management, stateless pattern |
| 15 | Servlet e JDBC: Accesso ai Database | SQL, connection pooling, CRUD operations |
| 16 | Servlet Filters: Pre e Post-Processing | Interceptor pattern, authentication, compression |
| 17 | CGI: Common Gateway Interface | Protocollo CGI (per contesto storico) |
| 18 | Jakarta Server Pages (JSP): Template Lato Server | JSP scriptlets, directives, expression language |
| 19 | JSTL: JSP Standard Tag Library | Tag library, i18n, iterazione, condizionali |
| 20 | Thymeleaf: Natural Templates Lato Server | Attributi HTML, th:each, th:text, dual display |

---

## Cap. 1 — Evoluzione delle Architetture Distribuite

### Argomento
Evoluzione storica dei sistemi informatici da mainframe monolitico al cloud computing.

### Temi Principali
- **One-Tier (1960–70)**: Mainframe + terminali stupidi; architetture monolitiche (IBM SNA, CICS, COBOL)
- **Two-Tier / Client-Server (1980)**: PC e DBMS separati; SQL; fat client problem
- **Three-Tier (1990+)**: Middle tier intermedio; RPC, OO, Message Oriented Middleware
- **Web Three-Tier**: Browser + HTTP + Web Server + Database
- **Rich Internet Applications (RIA)**: AJAX, HTML5; interazione asincrona
- **Mobile Apps**: Web mobile vs native
- **Service Oriented Architecture (SOA)**: Servizi, disaccoppiamento; WSDL/SOAP vs REST
- **Cloud Computing**: On-demand, elasticity, multi-tenancy; NIST model
- **Java Enterprise Edition (JEE / Jakarta EE)**: Servlet, JSP, JPA, JMS, EJB

### Tecnologie Citate
Mainframe (MVS), COBOL, Oracle Developer 2000, RPC, CORBA, Java RMI, DCOM, MOM/MQ, HTTP, AJAX, HTML5, SOA, WSDL, SOAP, REST, Cloud (AWS, Azure), Jakarta EE

### Concetti Chiave
- Accoppiamento/disaccoppiamento
- Scalabilità
- Separazione di responsabilità
- Distribuzione software
- Stateless vs stateful

---

## Cap. 2 — TCP/IP

### Argomento
Fondamenti del modello TCP/IP e del networking internet.

### Temi Principali
- **Modello OSI vs TCP/IP**: Layer 1–7 vs Layer 4 (Transport) e Layer 3 (Internet)
- **TCP (Transmission Control Protocol)**: Connection-oriented, reliable, stream-based; three-way handshake; ports
- **UDP (User Datagram Protocol)**: Connectionless, unreliable, datagram-based; bassa latenza
- **IP (Internet Protocol)**: Routing, IPv4, IPv6, indirizzi, subnet masks
- **DNS (Domain Name System)**: Risoluzione hostname → IP
- **Socket Programming**: Client-server communication via socket

### Tecnologie Citate
TCP, UDP, IP, DNS, Socket API, Telnet, SMTP, POP3, FTP, SSH

### Concetti Chiave
- Connection-oriented vs connectionless
- Reliability vs latency trade-off
- Port numbers, listening sockets
- Network layers
- Client-server model

---

## Cap. 3 — HTTP

### Argomento
Protocollo HTTP; metodi, header, status code, comunicazione client-server.

### Temi Principali
- **HTTP 1.0 / 1.1 / 2 / 3**: Versioni e differenze
- **Metodi HTTP**: GET (idempotent, safe), POST (non-idempotent), PUT, DELETE, HEAD, OPTIONS, PATCH
- **Request Line**: Metodo, URL, versione
- **Response Status**: 1xx (info), 2xx (success), 3xx (redirect), 4xx (client error), 5xx (server error)
- **Header HTTP**: Content-Type, Content-Length, Cache-Control, Expires, ETag, Last-Modified, Set-Cookie, Authorization, CORS headers
- **Body (Payload)**: HTML, JSON, binary data
- **Stateless vs Persistent Connection**: Keep-Alive, Connection management
- **HTTPS / TLS**: Crittografia, certificati
- **Caching**: Client-side vs server-side; ETag; Cache-Control directives
- **Conditional Requests**: If-Modified-Since, If-None-Match (304 Not Modified)

### Tecnologie Citate
HTTP/1.1, HTTP/2, HTTP/3, HTTPS, TLS, WebSocket (upgrade da HTTP), REST API (usa HTTP)

### Concetti Chiave
- Stateless protocol
- Safe methods (GET, HEAD) vs idempotent (GET, PUT, DELETE)
- Cache validation (ETags, Last-Modified)
- Content negotiation
- Status codes semantica
- CORS (Cross-Origin Resource Sharing)

---

## Cap. 4 — HTML: Fondamenti

### Argomento
Markup HTML, semantica, struttura di un documento, formulari.

### Temi Principali
- **Struttura Documento**: <!DOCTYPE>, <html>, <head>, <body>
- **Head Section**: <title>, <meta>, <link>, <script>, <style>
- **Semantic Tags**: <header>, <nav>, <main>, <article>, <section>, <aside>, <footer>
- **Text Content**: <h1>–<h6>, <p>, <blockquote>, <pre>, <code>, <ul>, <ol>, <li>
- **Links**: <a href="..."> absolute vs relative paths
- **Images**: <img src="..." alt="..."> responsive images
- **Tables**: <table>, <thead>, <tbody>, <tfoot>, <tr>, <td>, <th>; accessibility (caption, scope)
- **Formulari**: <form> action/method, <input> types (text, password, checkbox, radio, file, date, email), <textarea>, <select>, <label>, <fieldset>
- **Input Types HTML5**: color, range, number, email, url, search, tel, date, time, datetime-local, month, week
- **Form Validation**: required, pattern, min/max, step
- **Accessibility**: aria-* attributes, semantic markup, alt text

### Tecnologie Citate
HTML5, Web Accessibility Initiative (WAI), ARIA, CSS selectors

### Concetti Chiave
- Semantica (significato vs presentazione)
- Accessibilità (a11y)
- Separazione markup/style/behavior
- Form submission (GET vs POST)
- Validation client-side vs server-side
- Responsive design (viewport, flexible images)

---

## Cap. 5 — Client-Side Scripting: Introduzione

### Argomento
Introduzione al dynamic behavior nel browser; storia del JavaScript; quando eseguire codice lato client.

### Temi Principali
- **Dove eseguire il codice**: Logica di business che deve essere interattiva → client-side; logica sensibile → server-side
- **JavaScript**: Linguaggio di scripting per il browser; interpreted, dynamic typing
- **Inclusione Script**: <script src="..."> vs inline script; async, defer attributes
- **Timing**: DOMContentLoaded vs load event; quando il DOM è ready
- **Browser APIs**: Accesso alla finestra (window object), documento (document), navigatore
- **Console**: console.log(), console.error() per debugging
- **Versioni JavaScript**: ES5, ES6/ES2015, ES2016+; transpiling (Babel)

### Tecnologie Citate
JavaScript, Babel, TypeScript (menzione), npm/Node.js

### Concetti Chiave
- Execution context nel browser
- Synchronous vs asynchronous execution
- Global scope (window object)
- Document-ready pattern
- Unobtrusive JavaScript (separazione HTML/JS)

---

## Cap. 6 — JavaScript: Il Linguaggio

### Argomento
Sintassi JavaScript, tipi di dati, variabili, operatori, control flow.

### Temi Principali
- **Variabili**: var (hoisting), let, const (block scope)
- **Tipi Primitivi**: number, string, boolean, null, undefined, symbol, bigint
- **Type Coercion**: == vs ===, truthy/falsy values
- **Operatori**: Aritmetici, logici, bitwise, assignment, ternary, spread (...)
- **Control Flow**: if/else, switch, for, while, do-while, break, continue
- **Oggetti Letterali**: {} notation, property access (dot vs bracket), prototypal inheritance
- **Array**: Literal [], indexing, length, methods (push, pop, slice, map, filter, reduce, etc.)
- **String**: Literal quotes, template literals (`${expr}`), methods (charAt, substring, indexOf, split, replace)
- **typeof Operator**: Checking types (but careful with typeof null, typeof [])
- **Scope**: Global, function, block; closure
- **Hoisting**: Variable hoisting (var), function hoisting

### Tecnologie Citate
ES5, ES6/2015, RegExp (pattern matching)

### Concetti Chiave
- Dynamic typing (runtime type checking)
- Prototype-based inheritance
- Lexical scope
- Block scope (let/const vs var)
- Immutability (const objects are still mutable content)
- Type safety (linting with ESLint)

---

## Cap. 7 — JavaScript: Le Funzioni

### Argomento
Funzioni JavaScript, closure, callback, higher-order functions, patterns.

### Temi Principali
- **Dichiarazioni**: function declaration, expression, arrow (=>) syntax
- **Parameters & Arguments**: Named parameters, default values, ...rest, destructuring
- **Return Value**: Early return, implicit return (arrow functions)
- **Scope Interno**: Local scope, outer scope access
- **Closure**: Funzione che "ricorda" il contesto esterno; stato privato; factory pattern
- **First-Class Functions**: Funzioni come valori (passare a variabili, parametri, return values)
- **Higher-Order Functions**: Funzioni che accettano/ritornano funzioni; map(), filter(), reduce()
- **Callback Functions**: Passare funzione come argomento; error-first callbacks (Node.js style)
- **Promise-Based**: Introduzione (rimandato a Cap. 10)
- **this Binding**: call(), apply(), bind(); arrow function context
- **Function Composition**: Combinare funzioni; pipe, compose patterns
- **Memoization**: Cache di risultati per ottimizzazione
- **Currying**: Trasformazione multi-parametri → catena di funzioni unarie

### Tecnologie Citate
ES6 arrow functions, destructuring, rest/spread operator, Promise (anticipazione)

### Concetti Chiave
- Lexical scope (closure origin)
- Function as first-class objects
- Function composition
- Partial application / Currying
- this binding (call site vs declaration site)
- Callback pyramid (callback hell → Promises)

---

## Cap. 8 — HTML DOM API

### Argomento
Manipolazione del DOM; selezione, traversal, modifica di elementi.

### Temi Principali
- **DOM Structure**: Document tree, nodes (element, text, attribute, comment)
- **Selettori**: getElementById(), getElementsByTagName(), getElementsByClassName(), querySelector(), querySelectorAll()
- **Traversal**: parentElement, parentNode, children, childNodes, firstChild, lastChild, nextSibling, previousSibling, nextElementSibling
- **Proprietà Element**: tagName, className, classList (add, remove, toggle, contains), id, getAttribute, setAttribute, removeAttribute, dataset (data-* attributes)
- **Modifica DOM**: innerHTML (with XSS warning), textContent, appendChild(), removeChild(), insertBefore(), replaceChild(), cloneNode(), createElement()
- **Creazione di Fragment**: document.createDocumentFragment() per batch DOM operations
- **Reading Properties**: offsetWidth, offsetHeight, offsetLeft, offsetTop, scrollTop, scrollLeft, getBoundingClientRect()
- **CSS Classes via classList**: Aggiungere/togliere classi dinamicamente (preferire a setAttribute('class'))
- **Performance**: DOM reflow/repaint; batch updates; caching selectors

### Tecnologie Citate
DOM Level 3 API, ES6 selectors (querySelector, querySelectorAll), classList API, dataset API

### Concetti Chiave
- DOM as tree of objects
- Selettore versatility (ID vs class vs universal)
- Traversal up/down/sideways
- Performance (minimize reflow)
- XSS security (innerHTML sanitization)
- Modern vs legacy APIs (Node.removeChild() vs Element.remove())

---

## Cap. 9 — Gestione degli Eventi HTML

### Argomento
Event handling nel browser; event bubbling, delegation, event listener management.

### Temi Principali
- **Event Types**: click, dblclick, mouseenter, mouseleave, mouseover, mouseout, input, change, submit, focus, blur, keydown, keyup, keypress, load, unload, scroll, resize, error
- **Inline Handlers**: onclick="..." (deprecated ma ancora funzionale)
- **addEventListener()**: Multipli listener, removeEventListener()
- **Event Object**: event.type, event.target, event.currentTarget, event.preventDefault(), event.stopPropagation(), event.stopImmediatePropagation()
- **Event Phases**: Capture phase (down), target phase, bubbling phase (up)
- **Event Bubbling**: Propagazione da target verso root; preventDefault() vs stopPropagation()
- **Event Delegation**: Single listener su parent; usare event.target per identificare elemento
- **Capturing Phase**: addEventListener(handler, true) per capture invece di bubble
- **Mouse Events Coordinates**: clientX/clientY (viewport), pageX/pageY (document), screenX/screenY (screen)
- **Keyboard Events**: keyCode (deprecated), key (modern), Ctrl/Shift/Alt modifiers (event.ctrlKey, etc.)
- **Form Events**: submit, reset, input, change; form validation
- **Window Events**: resize, scroll, error, load, beforeunload
- **Touch Events** (su mobile): touchstart, touchmove, touchend, TouchList, Touch.clientX/Y

### Tecnologie Citate
DOM Event API, TouchEvent API, PointerEvent API (modern)

### Concetti Chiave
- Event-driven programming
- Bubbling vs capturing
- Event delegation (efficienza)
- preventDefault() vs stopPropagation() (loro effetti diversi)
- Memory leaks (se non si removeEventListener)
- Modern keyboard event handling (key property vs keyCode)

---

## Cap. 10 — Interazione Asincrona con XMLHttpRequest

### Argomento
AJAX; XMLHttpRequest; fetch API; comunicazione asincrona client-server senza ricaricare pagina.

### Temi Principali
- **XMLHttpRequest (XHR)**: Costruttore, open(), send(), onreadystatechange, readyState (0–4), status, responseText, responseXML
- **readyState Values**: 0=unsent, 1=opened, 2=headers received, 3=loading, 4=done
- **HTTP Status Handling**: Check status (200–299 success, else error)
- **Callback Hell**: Nested callback; Promise introduction as solution
- **fetch API** (Modern): Promise-based alternative; fetch(url, options) → Response → response.json(), response.text()
- **Request Headers**: Impostare con setRequestHeader() (XHR) o headers option (fetch)
- **Response Headers**: getResponseHeader(), getAllResponseHeaders()
- **CORS** (Cross-Origin Resource Sharing): Same-origin policy; CORS headers (Access-Control-Allow-Origin, etc.); preflight requests (OPTIONS)
- **Error Handling**: Network errors vs HTTP errors (fetch non rilancia su 4xx/5xx)
- **Timeout**: XHR.timeout, XHR.ontimeout
- **POST vs GET**: Payload in body (POST), query string (GET)
- **FormData**: Inviare form data as multipart/form-data
- **Promise Chain**: then(), catch(); elimina callback nesting
- **async/await** (ES2017): Sintassi su Promise; try/catch per error handling
- **Sending JSON**: setRequestHeader('Content-Type', 'application/json'); JSON.stringify()
- **Parsing Response**: response.json() (auto-parses JSON); manual JSON.parse() per XHR

### Tecnologie Citate
XMLHttpRequest, fetch API, Promise, async/await, CORS, JSON

### Concetti Chiave
- Asynchronous programming (non-blocking)
- AJAX (XMLHTTP + JavaScript)
- Promise-based async (vs callback)
- CORS policy (security)
- Error handling patterns
- Request/response cycle
- JSON as data interchange format

---

## Cap. 11 — Pattern Pure HTML vs RIA: Confronto

### Argomento
Confronto architetturale: server-side rendering (Pure HTML) vs client-side rendering (RIA).

### Temi Principali
- **Pure HTML (Server-Rendering)**: Ogni interazione → POST → server genera HTML → browser riceve full page → DOM rebuild
  - Vantaggi: SEO-friendly, progressive enhancement, semplice, no JS required
  - Svantaggi: User experience non fluida, full page reloads, più traffic
  
- **RIA (Rich Internet Application)**: Carica pagina iniziale; interazioni via AJAX → JSON da server → JS costruisce DOM lato client
  - Vantaggi: Fluida, no full reloads, single-page experience, offline potential
  - Svantaggi: JS required, SEO complesso, state management on client, larger initial load
  
- **State Management (RIA)**: Mantenere stato lato client vs server
- **Template Rendering**: Server-side (Thymeleaf, JSP) vs client-side (template literal, framework template)
- **Hybrid Approach**: Render iniziale server, poi RIA per interazioni
- **WebSocket** (mention): Real-time bidirectional communication (vs request-response HTTP)

### Tecnologie Citate
Server-side rendering (Thymeleaf, JSP), Client-side rendering (JavaScript frameworks), AJAX, WebSocket, Single-Page Application (SPA)

### Concetti Chiave
- Trade-off tra semplicità e UX
- SEO considerations
- Progressive enhancement
- State synchronization (server vs client)
- Separation of concerns (API vs rendering)

---

## Cap. 12 — HTML5: Il Web come Piattaforma Applicativa

### Argomento
API HTML5 che trasformano il browser in una piattaforma applicativa moderna.

### Temi Principali
- **Canvas**: <canvas> element, getContext('2d'), disegno procedural (moveTo, lineTo, fillStyle, fillRect, arc)
- **SVG**: Scalable Vector Graphics; <svg>, <circle>, <rect>, <path> (XML-based, DOM-queryable)
- **Web Storage**: localStorage (persistent), sessionStorage (session-scoped); key/value string store
  - Alternativa: IndexedDB (object store, indices, transactions)
  
- **Web Workers**: Esecuzione di JS in background thread (non blocking main thread); postMessage() communication
  - Uso: Heavy computation, socket polling senza freezing UI
  
- **Geolocation API**: navigator.geolocation.getCurrentPosition(), watchPosition(); Latitude/Longitude
- **File API**: <input type="file">, FileReader API (readAsText, readAsArrayBuffer, readAsDataURL), drag-and-drop files
- **History API**: history.pushState(), history.replaceState(), onpopstate event (SPA navigation)
- **Notification API**: Desktop notifications (notification permission)
- **Vibration API**: navigator.vibrate() on mobile
- **Battery API**: navigator.getBattery() (deprecated)
- **Request Animation Frame**: requestAnimationFrame() instead of setInterval for animations (sync with browser refresh)
- **Multimedia**: <audio>, <video> elements con controls; media events (play, pause, ended, timeupdate)
- **WebGL**: 3D graphics in canvas (advanced, not main focus of course but mentioned)

### Tecnologie Citate
HTML5, Canvas API, SVG, Web Storage, IndexedDB, Web Workers, Geolocation API, File API, History API, requestAnimationFrame, WebRTC (mention)

### Concetti Chiave
- Browser as application platform
- Offline-first (Web Storage)
- Asynchronous heavy lifting (Workers)
- Progressive enhancement (API detection)
- Performance optimization (requestAnimationFrame)
- Security (Geolocation permission, File API sandboxing)

---

## Cap. 13 — Java Servlet: Architettura e Fondamenti

### Argomento
Java Servlet; architecture, lifecycle, request/response handling, URL mapping, deployment.

### Temi Principali
- **Servlet Basics**: Classe che implementa javax.servlet.Servlet (ora jakarta.servlet.Servlet in JEE 8+)
- **Servlet Lifecycle**: init() (una sola volta), service() (ogni richiesta), destroy() (shutdown)
  - Parametri init: ServletConfig (nome, parametri init), ServletContext (app-wide)
  
- **HTTP Servlet**: javax.servlet.http.HttpServlet; metodi override: doGet(), doPost(), doPut(), doDelete()
- **Request Object**: HttpServletRequest; getParameter(), getParameterMap(), getPathInfo(), getQueryString(), getMethod(), getRequestURL(), headers
- **Response Object**: HttpServletResponse; getWriter() (PrintWriter), getOutputStream() (InputStream), setContentType(), setStatus(), sendRedirect(), sendError()
- **URL Mapping**: <url-pattern> in web.xml; exact (/login), wildcard (*.jsp), default (/)
- **URL-based vs Name-based Routing**: Servlet mapping per URL path
- **Deployment**: WAR file (Web Application Archive); web.xml deployment descriptor; context.xml
- **Static vs Dynamic Content**: Servlet per HTML generation dinamico
- **HTML Generation**: out.println("<html>...") (tedioso, preferire template engine)
- **Content-Type Header**: text/html, application/json, application/xml
- **Forwarding vs Redirect**: forward() (server-side, same request), sendRedirect() (client-side, new request with 302)

### Tecnologie Citate
Jakarta Servlet, HttpServlet, web.xml, WAR, Tomcat, Jakarta EE

### Concetti Chiave
- Request-response model
- Servlet lifecycle (init once, serve many)
- URL mapping (pattern matching)
- Server-side vs client-side redirect
- Stateless handler pattern
- Separation of controller (servlet) from view (template)

---

## Cap. 14 — Servlet: Sessioni, Cookie e Pattern di Comunicazione

### Argomento
State management in HTTP (stateless protocol); session, cookie, authentication patterns.

### Temi Principali
- **HTTP Stateless Nature**: Ogni richiesta è indipendente; server non mantiene memoria di richiedente
- **Cookie**: Small text stored on client; sent with every request in Cookie header; expires (session vs persistent); path, domain, secure, httpOnly
- **Session (Server-Side State)**: HttpSession object; getId(), getAttribute(), setAttribute(), removeAttribute(), invalidate()
  - Session ID inviato via cookie (JSESSIONID) o URL rewriting
  
- **Session Lifecycle**: Creation (accesso primo), persistence (cookie), timeout (default 30 min), invalidation (logout)
- **Cookie vs Session**: Cookie client-side, Session server-side; Session secure per sensitive data
- **User Authentication**: Login form (POST) → validate credentials → create session → redirect to app
  - Logout: invalidate session
  
- **Session Affinity**: User sempre va stesso server (se cluster); sticky session
- **Remember-Me**: Cookie persistente vs session cookie
- **Token-Based Auth** (mention): JWT (rimandato a backend avanzato)
- **CSRF Protection**: Cross-Site Request Forgery; token-based validation
- **Hidden Form Field Pattern**: Passare dati tra pagine via form nascosto (alternativa sessione, meno used)
- **Pattern Stateless**: Alternative a session (API tokens, JWT)

### Tecnologie Citate
HttpSession, Cookie (RFC 6265), JSESSIONID, Servlet HttpSession API, CSRF tokens

### Concetti Chiave
- Stateless → state management
- Client-side (cookie) vs server-side (session) state
- Session security (hijacking, fixation)
- Logout & session invalidation
- Multi-server deployment (session replication vs sticky)

---

## Cap. 15 — Servlet e JDBC: Accesso ai Database

### Argomento
JDBC API; SQL queries; connection management; DAO pattern; transactions; SQL injection prevention.

### Temi Principali
- **JDBC Basics**: Java Database Connectivity; java.sql.* classes
- **Connection**: DriverManager.getConnection(url, user, pwd); Connection interface; close() per liberare risorse
- **PreparedStatement**: Compiled query; setString(), setInt(), setDate() per parametri; executeQuery() / executeUpdate()
  - Vantaggio: SQL injection prevention (parametri escaped)
  - Advantage: Query caching (performance)
  
- **Statement** (Legacy, Deprecated): Non usare per input utente (SQL injection risk)
- **ResultSet**: Risultati query; next(), getInt(), getString(), getDate(); TYPE_FORWARD_ONLY, TYPE_SCROLL_INSENSITIVE
- **CRUD Operations**: Create (INSERT), Read (SELECT), Update (UPDATE), Delete (DELETE)
- **Connection Pooling**: DataSource; pool di connection riusabili (Apache DBCP, HikariCP); getConnection() da pool
- **Transactions**: autocommit (default true); begin, commit, rollback; setAutoCommit(false), commit(), rollback()
  - ACID properties (Atomicity, Consistency, Isolation, Durability)
  - Isolation level (READ_UNCOMMITTED, READ_COMMITTED, REPEATABLE_READ, SERIALIZABLE)
  
- **DAO Pattern**: Data Access Object; separa SQL logic da business logic
  - Entity class (POJO) → DAO class (database operations) → Servlet (controller)
  
- **Mapping Rows to Objects**: ResultSet → Object (manual mapping)
- **Batch Processing**: addBatch(), executeBatch() per multiple statements
- **Escaping SQL**: PreparedStatement fa per te; avoid string concatenation
- **Database Credentials**: From web.xml context-param (non hardcoded)
- **Error Handling**: SQLException; finally block per chiudere resources; try-with-resources (Java 7+)

### Tecnologie Citate
JDBC, PreparedStatement, Connection Pooling (DBCP, HikariCP), DAO pattern, MySQL, SQL standard

### Concetti Chiave
- Connection lifecycle (open, use, close)
- SQL injection and prevention
- Parameterized queries
- Transaction management
- Connection pooling for scalability
- DAO pattern (separation of concerns)
- Resource cleanup (try-with-resources)

---

## Cap. 16 — Servlet Filters: Pre e Post-Processing

### Argomento
Servlet Filters; interceptor pattern; preprocessing e postprocessing di request/response.

### Temi Principali
- **Filter Interface**: javax.servlet.Filter; init(), doFilter(ServletRequest, ServletResponse, FilterChain), destroy()
- **Filter Chain**: FilterChain.doFilter() passa controllo al prossimo filter/servlet
- **doFilter() Signature**: Può intercettare PRE (prima servlet) e POST (dopo servlet) nella catena
- **Mapping**: <filter> + <filter-mapping> in web.xml; url-pattern
- **Uso Comuni**:
  - Logging (request method, path, timestamp)
  - Authentication / Authorization (check session, redirect se non autenticato)
  - Input Validation (strip/validate parameters)
  - Encoding (setCharacterEncoding('UTF-8'))
  - Compression (gzip response)
  - CORS headers aggiunta
  - Security headers (X-Frame-Options, Content-Security-Policy)
  - Performance monitoring
  
- **Wrapper Pattern**: HttpServletRequestWrapper, HttpServletResponseWrapper per wrapped request/response
  - Utile per modificare behavior (e.g., buffer response per logging)
  
- **doFilter Sequence**: request → Filter 1 → Filter 2 → Servlet → Filter 2 → Filter 1 → response
- **Multiple Filters**: Ordine definito da web.xml sequenza <filter-mapping>
- **Exception Handling**: FilterChain.doFilter() throws ServletException; catch/rethrow o handle

### Tecnologie Citate
Servlet Filters, FilterChain, Wrapper pattern, web.xml filter configuration

### Concetti Chiave
- Interceptor pattern
- Chain of responsibility
- Cross-cutting concerns (logging, auth, security)
- Request/response decoration (wrapper)
- Filter ordering (important)

---

## Cap. 17 — CGI: Common Gateway Interface

### Argomento
CGI protocol (contesto storico); come i server web lanciavano programmi per generare HTML dinamico.

### Temi Principali
- **CGI History**: Prima di Servlet/JSP; meccanismo per lanciare programmi lato server
- **Overhead**: Process per request (molto lento vs servlet/thread)
- **Environment Variables**: Parametri passati al programma CGI via env var (REQUEST_METHOD, QUERY_STRING, CONTENT_LENGTH, etc.)
- **stdin/stdout**: Input da stdin, output su stdout (reindirizzato a client)
- **Perl, C, Shell Script**: Linguaggi comuni per CGI
- **vs Servlet**: Servlet = thread (fast), CGI = process (slow); Servlet memoria persistente, CGI every request; Servlet in Java, CGI in qualsiasi linguaggio
- **FastCGI, SCGI**: Tentativi di migliorare CGI (pooled process/persistent connection); Apache mod_fcgid

### Tecnologie Citate
CGI, FastCGI, Perl, C, Apache, mod_cgi, mod_fcgid

### Concetti Chiave
- Process-based vs thread-based execution
- Historical perspective (why Servlet improvement)
- Performance implications
- Language neutrality (CGI any language)

---

## Cap. 18 — Jakarta Server Pages (JSP): Template Lato Server

### Argomento
JSP; embedding Java in HTML; JSP lifecycle; expression language; directives; request/response in template context.

### Temi Principali
- **JSP Basics**: HTML + Java code; file .jsp → compilato a Servlet; output PrintWriter
- **Scriptlet (Deprecated)**: <% ... %> Java code block; <% out.println(...) %> per output
  - Sconsigliato (unreadable, hard to debug); usare EL e tag library invece
  
- **Expression**: <%= expr %> valuta expr e stampa; abbreviazione per out.print()
- **Directive**: <%@ page ... %> (import, contentType, pageEncoding); <%@ include ... %> (static include)
- **Declaration**: <%! ... %> metodo/campo dichiarato in servlet class scope (raro)
- **Implicit Objects**: request (HttpServletRequest), response (HttpServletResponse), session (HttpSession), application (ServletContext), out (PrintWriter), pageContext, exception, config, page
- **Expression Language (EL)**: ${expr} accede beans/map; ${'value'} string literal; ${null} undefined handling
  - Operators: ., [], +, -, *, /, %, >, <, ==, !=, &&, ||, !
  - Scope: pageScope, requestScope, sessionScope, applicationScope
  
- **Request Data Binding**: ${param.fieldName} accede URL parameter; ${requestScope.attribute}
- **JSP Lifecycle**: .jsp → compilato a .class (Servlet) → init() once → _jspService() ogni richiesta
- **Best Practices**: Minimizzare scriptlet; usare EL + JSTL; separare logica da presentazione
- **Error Handling**: <%@ page errorPage="error.jsp" %> redirect error template; exception implicit object
- **Page Caching**: Non caching JSP dinamica (cache server-side data invece)

### Tecnologie Citate
JSP, EL (Expression Language), JSTL (topic of next chapter), implicit objects, servlet compilation

### Concetti Chiave
- Template-based server-side rendering
- JSP lifecycle (compile once, serve many)
- EL for data binding
- Implicit objects availability
- Separation of presentation from logic (via tag lib)

---

## Cap. 19 — JSTL: JSP Standard Tag Library

### Argomento
JSTL; replacing scriptlet with standardized tags; iteration, conditionals, core, fmt, sql tags.

### Temi Principali
- **JSTL Overview**: Standard tag library; XML syntax; eliminare scriptlet Java da JSP
- **Core Tags (<c:...))**:
  - <c:forEach var="item" items="${list}"> iterazione
  - <c:forTokens items="${csv}" delims=","> split string
  - <c:if test="${condition}"> conditional
  - <c:choose>, <c:when>, <c:otherwise> (switch-like)
  - <c:out value="${expr}"> output (default behavior, no EL needed)
  - <c:set var="name" scope="request" value="${expr}"> set variable
  - <c:remove var="name"> remove variable
  - <c:catch var="exception"> exception handling
  - <c:redirect url="..."> redirect
  
- **Formatting Tags (<fmt:...))**:
  - <fmt:formatNumber value="${num}" pattern="0.00"> number formatting
  - <fmt:formatDate value="${date}" pattern="dd/MM/yyyy"> date formatting
  - <fmt:parseDate> parse date string to Date
  - <fmt:setLocale value="en_US"> i18n locale
  - <fmt:setBundle basename="messages"> load property file
  - <fmt:message key="error.message"/> lookup i18n message
  
- **SQL Tags (<sql:...)** (Sconsigliato, usare DAO): 
  - <sql:setDataSource> define DataSource
  - <sql:query> execute SELECT
  - <sql:update> execute INSERT/UPDATE/DELETE
  - Disadvantage: SQL in JSP (bad separation, hard to test)
  
- **Functions (<fn:...))**:
  - ${fn:length(list)} → list.size()
  - ${fn:substring(str, start, end)}
  - ${fn:toUpperCase(str)}, ${fn:toLowerCase(str)}
  - ${fn:contains(str, substr)}
  - ${fn:split(str, delim)}
  - ${fn:join(array, sep)}
  
- **Internationalization (i18n)**: locale-specific messages; property file per lingua
- **Taglib Declaration**: <%@ taglib uri="..." prefix="c" %> import tag library
- **Expression Language vs Scriptlet**: EL ${...} more readable; no scriptlet <% %>
- **Best Practice**: JSTL + EL for view; DAO/Service layer for logic

### Tecnologie Citate
JSTL 1.2, EL 2.1+, i18n with .properties files, MessageFormat (Java utility)

### Concetti Chiave
- Template tags vs programming language
- Readability (XML tags vs Java scriptlet)
- i18n/l10n (locale-specific content)
- Separation of concerns (presentation ≠ logic)
- Iteration and conditionals as tag library

---

## Cap. 20 — Thymeleaf: Natural Templates Lato Server

### Argomento
Thymeleaf; naturali template HTML; dual display (static preview + dynamic content); attributi th:*; Spring integration.

### Temi Principali
- **Thymeleaf Overview**: Template engine che produce HTML naturale (non tag library sintassi)
  - File HTML valido che il browser visualizza (mockup)
  - Server elabora e sostituisce placeholder con dati reali
  
- **Attribute Processing**: th:* attribute su tag HTML normali
  - th:text="${expr}" imposta textContent
  - th:utext="${expr}" unescaped HTML (careful XSS)
  - th:attr="attr=value, attr2=value2" imposta attributi multipli
  - th:href="@{/path}" URL building
  - th:action="@{/form}" form action
  - th:value="${expr}" input value
  - th:selected="${condition}" select option
  - th:checked="${condition}" checkbox/radio
  
- **Expression Language**: ${expr} access variables, properties, methods
  - ? operator (Elvis): ${param?.name} null-safe
  - Inline text: [[${expr}]] (inside text content, processed by server)
  - Inline script: /*[[${expr}]]*/ (inside JS comment, processed)
  
- **Iteration**: th:each="item, stat : ${list}"
  - stat object: index, count, size, odd/even, first/last, current
  
- **Conditionals**: th:if, th:unless (negation), th:switch/th:case
- **Remove**: th:remove="all" (remove element), "body" (keep tag, remove children), "tag" (remove tag, keep children), "all-but-first" (keep first, remove others)
  - Dual display: mockup HTML visible in browser, removed by server
  
- **Form Handling**: th:object="${command}" per form binding; *{field} shorthand per properties
  - Thymeleaf form tag helper (action, method, fields)
  
- **URL Building**: @{/path(param=value)} create URL with parameters
- **Encoding**: automatic HTML entity encoding (" → &quot;, < → &lt;)
  - th:utext to output unescaped (use carefully)
  
- **Fragment Reuse**: th:fragment="name" define template fragment; th:insert/th:replace/th:include reuse
- **Special Objects**:
  - #ctx (context), #locale (language/country), #request, #response, #session, #servletContext
  - #lists (isEmpty, size, contains)
  - #strings (substring, contains, startsWith, endsWith, toUpperCase, toLowerCase)
  - #dates (format, parse)
  - #numbers (format, formatDecimal)
  
- **Comparison to JSP/JSTL**: Thymeleaf HTML è valido; browser mostra preview; JSTL tag <c:forEach> non visualizzabile
- **Performance**: Thymeleaf caching di template compilati
- **Spring Integration**: Thymeleaf default template engine in Spring Boot

### Tecnologie Citate
Thymeleaf, Spring MVC integration, expression language, template fragments

### Concetti Chiave
- Natural templates (valid HTML)
- Dual display (static mockup + dynamic rendering)
- Attribute processors
- Fragments for template reuse
- Clean separation of concerns
- Null-safe operators
- URL building
- Default choice in modern Spring Boot

---

## Mappa di Connessioni Trasversali

### Frontend (Cap. 4–12)
- **HTML** (Cap. 4) → **Struttura semantica**
- **JavaScript** (Cap. 6–7) → **Client-side behavior**
- **DOM API** (Cap. 8) → **Manipolazione elementi**
- **Events** (Cap. 9) → **Interazione utente**
- **AJAX/Fetch** (Cap. 10) → **Comunicazione asincrona con server**
- **Pure HTML vs RIA** (Cap. 11) → **Architettura: server-side vs client-side rendering**
- **HTML5 APIs** (Cap. 12) → **Moderne capacità browser** (Canvas, Storage, Workers, Geolocation)

### Backend (Cap. 13–20)
- **Servlet** (Cap. 13) → **HTTP handler, controller**
- **Session/Cookie** (Cap. 14) → **State management**
- **JDBC** (Cap. 15) → **Database access, DAO pattern**
- **Filters** (Cap. 16) → **Request/response interception, cross-cutting concerns**
- **JSP/JSTL** (Cap. 18–19) → **Server-side template, dynamic HTML generation**
- **Thymeleaf** (Cap. 20) → **Modern template engine, natural HTML**

### Networking & Protocols (Cap. 2–3)
- **TCP/IP** (Cap. 2) → **Fondamento comunicazione internet**
- **HTTP** (Cap. 3) → **Protocollo web, metodi, status**
- → **Utilizzato da Servlet (Cap. 13) per request/response**
- → **Utilizzato da AJAX (Cap. 10) per comunicazione asincrona**

### Architettura (Cap. 1)
- **Three-Tier Web Architecture**: Browser client (Cap. 4–12) → Web Server + Servlet (Cap. 13–16) → Database (Cap. 15)
- **Pure HTML** (Cap. 11): Server-side rendering (Cap. 18–20)
- **RIA** (Cap. 11): Client-side rendering con AJAX (Cap. 10) + JavaScript (Cap. 6–7) + API (Cap. 12)

---

## Tecnologie Principali Citate nel Corso

### Client-Side
- **HTML5**: Markup, forms, semantic tags
- **CSS3**: Styling (non approfondito nel corso, ma assunto)
- **JavaScript (ES6+)**: Core language, async/await, arrow functions
- **DOM API**: Selectors, traversal, manipulation
- **Event Listeners**: onClick, onChange, onSubmit, onLoad
- **AJAX/Fetch**: XMLHttpRequest, fetch API, Promises
- **Web APIs**: Canvas, Storage (localStorage/sessionStorage), Workers, Geolocation, File API, History API
- **HTML5 Multimedia**: <audio>, <video>, WebGL (mention)

### Server-Side
- **Java**: Java 8+ features (lambda, stream API)
- **Jakarta Servlet API**: HttpServlet, request/response, filters
- **JDBC**: Database connectivity, PreparedStatement, transactions
- **JSP**: Server-side template (legacy, for understanding)
- **JSTL**: Tag library (legacy, for understanding)
- **Thymeleaf**: Modern template engine (recommended)

### Databases
- **MySQL**: DBMS used in course examples
- **SQL**: DDL (CREATE TABLE), DML (INSERT, SELECT, UPDATE, DELETE)
- **JDBC**: Java driver to MySQL
- **Connection Pooling**: Apache DBCP, HikariCP (mention)

### Protocol & Standards
- **TCP/IP**: Networking foundation
- **HTTP/1.1, HTTP/2**: Web protocol (methods, status codes, headers)
- **HTTPS/TLS**: Secure communication
- **CORS**: Cross-origin requests
- **JSON**: Data format (AJAX response)
- **XML**: Historically used (JSP responseXML, SOAP)

### Tools & Frameworks
- **Tomcat 10.1**: Servlet container (runtime environment)
- **Eclipse IDE**: Java development (mentioned as deployment platform)
- **IntelliJ IDEA**: Alternative IDE (guide provided)
- **Spring Boot** (mentioned): Modern Java web framework (optional, advanced topic)
- **Maven/Gradle**: Build tool (not primary focus, Eclipse projects used)

### Architecture Patterns
- **MVC**: Model-View-Controller (Servlet as controller, JSP/Thymeleaf as view)
- **DAO**: Data Access Object (separation of data logic)
- **Singleton Pattern**: Servlet instance, connection pool
- **Factory Pattern**: DataSource (connection creation)
- **Filter/Interceptor**: Servlet Filters, cross-cutting concerns
- **Three-Tier**: Client → Web Server → Database

### Security Topics
- **SQL Injection**: PreparedStatement prevention
- **XSS (Cross-Site Scripting)**: HTML entity encoding, innerHTML sanitization
- **CSRF (Cross-Site Request Forgery)**: Token-based validation
- **CORS**: Same-origin policy, preflight requests
- **Session Hijacking**: Session security, httpOnly cookie flag
- **Password Storage**: Hash (not plaintext) — topic for advanced course

---

## Glossario Rapido

| Termine | Significato |
|---------|------------|
| **AJAX** | Asynchronous JavaScript and XML; comunicazione asincrona client-server |
| **API** | Application Programming Interface; interfaccia pubblica |
| **Callback** | Funzione passata come argomento, eseguita dopo evento |
| **CORS** | Cross-Origin Resource Sharing; policy che consente richieste da origin diverso |
| **DAO** | Data Access Object; pattern per isolate database logic |
| **DOM** | Document Object Model; tree di elementi HTML |
| **EL** | Expression Language; sintassi ${expr} in JSP |
| **JSTL** | JSP Standard Tag Library; tag library per template |
| **MIME Type** | Content-Type header (text/html, application/json, etc.) |
| **MVC** | Model-View-Controller; architettura separazione responsabilità |
| **RIA** | Rich Internet Application; web app con UX type desktop |
| **Servlet** | Java class che gestisce HTTP request/response |
| **SPA** | Single-Page Application; app web che non ricarga pagina |
| **SQL Injection** | Attacco inserendo SQL maligna in input utente |
| **URI** | Uniform Resource Identifier; address of resource |
| **URL** | Uniform Resource Locator; indirizzo web (tipo URI) |
| **XSS** | Cross-Site Scripting; attacco iniettando JavaScript |

---

## Come Usare Questo Documento

1. **Ricerca rapida argomento**: Usa Ctrl+F per cercare capitolo (es. "Cap. 10")
2. **Drill-down tematico**: Leggi sezione "Temi Principali" per overview, poi visita Appunti Completi per dettagli
3. **Contesto architetturale**: Vedi sezione "Mappa di Connessioni Trasversali"
4. **Ricerca tecnologia**: Usa Glossario o tabella "Tecnologie Citate"
5. **Pattern di sviluppo**: Sezione "Architecture Patterns" per best practices

---

**Documento creato**: Aprile 2026  
**Versione**: 1.0  
**Basato su**: TIW_Appunti_Completi.docx (Prof. Piero Fraternali, Politecnico di Milano)