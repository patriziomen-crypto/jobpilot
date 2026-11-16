# Piano: JobPilot — app personale per gestire candidature LinkedIn / Indeed

## Contesto

Patrizio (Junior Full Stack: Java, Angular, MySQL, zona Pomezia/Roma) vuole un software personale che
gestisca la ricerca di lavoro su LinkedIn e Indeed: trovare offerte, preparare CV e lettera di
presentazione su misura per ogni annuncio, inviare la candidatura e tenere traccia di tutto.
La cartella `Gestione-Linkedin` contiene oggi solo il CV in formato `.docx`.

Vincolo di fondo: LinkedIn e Indeed **non offrono API pubbliche per candidarsi** e i loro termini
vietano i bot che inviano candidature. Per questo l'app è **semi-automatica**: fa tutto il lavoro
pesante (ricerca, analisi, CV e lettera personalizzati, pacchetto pronto, tracking) e l'utente
preme "Invia" sul sito. Zero rischio di ban, nessuna rottura quando LinkedIn cambia layout.

Il progetto è anche un pezzo di **portfolio**: stack identico a quello del CV.

## Decisioni prese (con l'utente, 13 nov 2026)

| Tema | Scelta |
|---|---|
| Automazione invio | Semi-automatica: l'app prepara tutto, l'utente clicca "Invia" |
| Stack | Java 25 LTS + Spring Boot 4.1.2 (Maven wrapper) + Angular 22 + MySQL 9.7 LTS (versioni più recenti, richiesto dall'utente; JDK 25 e MySQL 9.7 installati via winget) |
| AI | API Claude, SDK ufficiale Java `com.anthropic:anthropic-java:3.3.0`, modello `claude-opus-5-5` |
| Target | Junior developer / programmatore, stage e tirocini, Roma + remoto Italia; annunci in italiano |
| Distribuzione | **App desktop Windows**: un `JobPilot.exe` con icona sul Desktop, nessun browser da aprire a mano |

Ambiente: Oracle JDK 25 e Node.js 26 LTS installati via winget il 13 nov 2026 (Angular 22 richiede
Node ≥ 24.15); MySQL Server 9.7.4 installato come servizio `MySQL97` sulla porta **3307**, accanto al
vecchio MySQL 8.0 che resta sulla 3306 (script riutilizzabile in `scripts/setup-mysql97.ps1`).
Database `jobpilot`, utente `jobpilot`. Non ci sono Maven né Docker: si usa `mvnw` di Spring Initializr.

## Architettura

```
┌──────────────────────────┐      REST/JSON      ┌─────────────────────────────┐
│  Angular 22 (frontend/)  │ ◄─────────────────► │  Spring Boot 4.1 (backend/) │
│  Dashboard Kanban        │                     │  ├─ jobs: import + parsing  │
│  Offerte / Analisi       │                     │  ├─ ai: Claude SDK          │
│  Candidatura guidata     │                     │  ├─ documents: PDF CV+letter│
│  Profilo / Impostazioni  │                     │  ├─ applications: tracking  │
└──────────────────────────┘                     │  └─ profile: dati master CV │
         ▲                                       └──────┬──────────┬───────────┘
         │ bookmarklet "Salva in JobPilot"               │          │
   Browser (LinkedIn/Indeed aperti, loggato)          MySQL     Claude API
                                                   jobpilot    (claude-opus-5-5)
```

Tutto gira in locale su `localhost`. Nessun server esterno a parte Claude.

### Come diventa un'app desktop

Un solo eseguibile, costruito con strumenti già inclusi nel JDK 25:

1. Il build Angular (`ng build`) viene copiato in `backend/src/main/resources/static/`: il JAR
   Spring Boot serve da solo frontend + API. Un unico processo, una sola porta.
2. Classe `DesktopLauncher`: all'avvio sceglie una porta libera, mostra un'icona nella **system
   tray** (menu: Apri, Apri cartella PDF, Esci) e apre la UI in una finestra senza barra URL
   (`msedge --app=http://127.0.0.1:<porta>` o Chrome; fallback al browser predefinito).
   Chiudere la finestra non spegne il backend; "Esci" dalla tray sì.
3. `jpackage` (nel JDK) impacchetta JAR + runtime Java ridotto in `JobPilot.exe` + installer
   `.msi`, con icona e collegamento sul Desktop. Profilo Maven `desktop` che esegue in sequenza
   `ng build` → `mvnw package` → `jpackage`.
4. Dati: il DB resta sul MySQL locale già installato; PDF e log in
   `%LOCALAPPDATA%\JobPilot\` così l'app funziona anche se la cartella del progetto si sposta.

Alternativa scartata: Electron. Aggiungerebbe un secondo runtime (Node) da impacchettare e
sincronizzare con il backend Java, senza vantaggi per un'app a uso personale.

### Struttura cartelle

```
Gestione-Linkedin/
├── backend/                  Spring Boot 4.1 (Maven wrapper, Java 25)
│   └── src/main/java/it/patrizio/jobpilot/
│       ├── profile/          entità Profile, esperienze, skill, risposte standard
│       ├── jobs/             JobOffer, import da URL / testo / bookmarklet, parser
│       ├── ai/               ClaudeService, prompt, record per structured output
│       ├── documents/        template Thymeleaf -> PDF (openhtmltopdf)
│       ├── applications/     Application, stati, note, follow-up
│       └── config/           CORS, proprietà, chiave API
├── frontend/                 Angular 22 standalone + Angular Material
├── bookmarklet/              1 file JS: cattura la pagina aperta e la POSTa al backend
├── output/                   PDF generati, 1 cartella per candidatura (gitignored)
├── docs/                     questo piano, decisioni, note ToS
└── Curriculum Vitae ... .docx   CV originale (fonte per il profilo master)
```

## Modello dati (MySQL, migrazioni Flyway)

- **profile** (1 riga): dati anagrafici, sommario, link, lingue, mobilità.
- **experience / education / skill**: voci del CV master con bullet "veri" (l'AI può solo
  selezionare, riordinare e riformulare, mai inventare).
- **standard_answer**: domande ricorrenti di Easy Apply / Indeed (anni di esperienza, RAL attesa,
  disponibilità, autorizzazione lavoro) con risposta pronta da copiare.
- **job_offer**: source (LINKEDIN/INDEED/ALTRO), url, titolo, azienda, luogo, remoto, descrizione
  grezza, data trovata, hash per dedup.
- **job_analysis**: output AI per offerta: punteggio compatibilità 0-100, requisiti soddisfatti /
  mancanti, parole chiave, lingua annuncio, red flag (es. senior mascherato da junior).
- **application**: stato (TROVATA → DA_VALUTARE → PRONTA → INVIATA → RISPOSTA → COLLOQUIO →
  OFFERTA / RIFIUTATA / SCADUTA), data invio, percorso PDF CV, testo lettera, note, data follow-up.

## Fasi di sviluppo

### Fase 0 — Setup (mezza giornata)
1. Genera backend con Spring Initializr (Web, Data JPA, Validation, Flyway, MySQL driver, Thymeleaf,
   Lombok). Aggiungi `anthropic-java:3.3.0` e `openhtmltopdf-pdfbox`.
2. Crea DB `jobpilot` su MySQL locale; credenziali in `application-local.yml` (gitignored).
3. `ng new frontend` (standalone, routing, SCSS) + Angular Material; proxy verso `:8080`.
4. `git init`, `.gitignore` (output/, *-local.yml, node_modules, target), README iniziale.
5. Chiave API Claude in variabile d'ambiente `ANTHROPIC_API_KEY`; `AnthropicOkHttpClient.fromEnv()`.

### Fase 1 — MVP "pacchetto candidatura" (1-2 settimane) ← il valore vero
1. **Profilo master**: importa i dati dal `.docx` una volta (script di seed Flyway o form Angular),
   pagina Profilo per modificarli + risposte standard.
2. **Import offerta manuale**: form con URL + incolla descrizione. Il backend prova a scaricare la
   pagina (LinkedIn `/jobs/view/<id>` espone JSON-LD `JobPosting`); se fallisce (Indeed blocca i
   bot), si usa il testo incollato. Dedup per hash URL.
3. **Analisi AI** (`ClaudeService.analyze`): structured output tipizzato
   `.outputConfig(JobAnalysis.class)` → punteggio, requisiti, keyword, lingua. Profilo master nel
   system prompt con `cacheControl` ephemeral (stesso prefisso per ogni chiamata = cache hit).
4. **CV su misura** (`ClaudeService.tailorCv`): structured output `TailoredCv` (sommario riscritto,
   bullet selezionati/riformulati, skill ordinate per rilevanza). Regola dura nel prompt: nessuna
   competenza o esperienza non presente nel profilo. Render Thymeleaf → PDF in
   `output/<data>_<azienda>_<ruolo>/CV_Patrizio_Menegoni.pdf`.
5. **Lettera di presentazione** (`ClaudeService.coverLetter`): testo + PDF, tono e lingua
   dell'annuncio, max 1 pagina, con 2-3 agganci concreti all'azienda/annuncio.
6. **Candidatura guidata** (wizard Angular): ① riepilogo analisi ② anteprima CV e lettera con
   modifica manuale e "rigenera" ③ pulsante "Apri annuncio" + lettera negli appunti + apri cartella
   PDF + risposte standard visibili ④ "Segna come inviata". Stato → INVIATA.
7. **Dashboard Kanban** con drag & drop tra stati, filtro per fonte, ricerca.

### Fase 2 — Flusso veloce (1 settimana)
1. **Bookmarklet** `bookmarklet/salva-in-jobpilot.js`: con LinkedIn/Indeed aperto nel browser
   loggato, un click invia `{url, title, html/testo}` a `POST /api/jobs/import`. Niente scraping
   server-side, niente ToS violati: è la tua pagina nel tuo browser.
2. Parser specifici per fonte (`LinkedInJobParser`, `IndeedJobParser`) dal testo catturato.
3. **Follow-up**: reminder automatici (7 giorni dopo INVIATA senza risposta) in dashboard.
4. **Statistiche**: candidature/settimana, tasso risposta per fonte, punteggio medio.

### Fase 2b — App desktop (2-3 giorni, subito dopo l'MVP)
1. Copia del build Angular nelle risorse statiche del backend (plugin `frontend-maven-plugin` o
   script `build-desktop.ps1`).
2. `DesktopLauncher` con tray icon, scelta porta libera, apertura finestra in modalità app.
3. Profilo Maven `desktop` + `jpackage` → `dist/JobPilot.exe` e `JobPilot-1.0.msi`.
4. Installazione: icona sul Desktop, avvio con doppio click, primo avvio chiede chiave Claude e
   credenziali MySQL e le salva in `%LOCALAPPDATA%\JobPilot\config.properties`.
5. Icona `.ico` dell'app in `backend/src/main/resources/icons/`.

### Fase 3 — Scoperta offerte (opzionale, 1 settimana)
1. **Job alert via email**: LinkedIn e Indeed inviano alert con link; lettura casella Gmail via
   IMAP (app password) → estrazione link → import automatico in stato TROVATA. Metodo stabile e
   conforme ai termini.
2. Ricerca su fonti con API aperte (es. Adzuna, feed aziendali) per allargare oltre LinkedIn/Indeed.
3. Batch notturno: analisi AI di tutte le offerte nuove, notifica delle ≥ 70/100.

### Fase 4 — Più persone e AI intercambiabile (fatta il 16 nov 2026)
1. **Più persone**: tabella `profile` con più righe; `application` e `job_analysis` legate alla persona
   (`profile_id`, analisi per candidatura); `profile_search` con le parole chiave di ciascuno. Il frontend
   manda l'header `X-Profile-Id`; selettore in alto a destra con "Nuova persona" (copia opzionale di
   competenze, risposte pronte e ricerca). PDF in `output/<persona>/<data_azienda_ruolo>/`.
2. **Fornitori AI**: interfaccia `AiProvider` con tre implementazioni: `ClaudeProvider` (SDK),
   `OpenAiCompatibleProvider` (OpenAI, GitHub Models, Gemini, Groq, Mistral, OpenRouter, Ollama, altro) e
   `TemplateProvider` (modalità base senza AI). `AiRouter` prova i fornitori attivi in ordine e ripiega
   sul successivo quando uno dà errore di chiave, credito, limite o rete; la modalità base chiude sempre.
   Pagina Impostazioni con preset, ordine, test di connessione; chiavi in `jobpilot.properties`.
3. **Ricerca automatica** (dalla Fase 2, ora prioritaria): LinkedIn via pagine pubbliche guest (senza
   account), Indeed via Edge pilotato da Playwright con profilo dedicato (login dell'utente una tantum
   dal pulsante "Accedi a Indeed"); filtro degli annunci sponsorizzati fuori tema; analisi automatica dei
   nuovi annunci; ricerca programmata alle 8 per chi l'attiva.

### Fuori scopo (decisione presa)
Bot Playwright che compila/invia Easy Apply: rischio ban, manutenzione continua. Se in futuro lo
si vuole, va in un modulo separato con conferma manuale prima dell'invio.

## Dettagli tecnici chiave

- **Claude SDK Java**: `MessageCreateParams.builder().model("claude-opus-5-5").maxTokens(16000L)
  .outputConfig(JobAnalysis.class)` → `client.messages().create(params)`; thinking adattivo attivo
  di default, `OutputConfig.Effort.MEDIUM` per analisi, `HIGH` per CV/lettera. Gestire
  `stop_reason == refusal` prima di leggere `content`. Costo stimato: ~0,05 € per candidatura
  completa (analisi + CV + lettera); cache sul profilo master riduce ulteriormente.
- **Qualità AI**: file `docs/prompts.md` con i prompt versionati; 5 annunci reali salvati in
  `backend/src/test/resources/jobs/` come casi di test per confrontare le uscite dopo ogni modifica
  ai prompt.
- **PDF**: template HTML/CSS in `backend/src/main/resources/templates/cv.html` e `letter.html`;
  stesso layout del CV attuale (intestazione, profilo, formazione, esperienza, competenze, lingue).
- **Sicurezza**: nessuna credenziale LinkedIn/Indeed salvata (non servono). Chiave Claude solo in
  env. Backend in ascolto solo su `127.0.0.1`. CORS limitato a `localhost:4200` + bookmarklet.
- **Errori**: catena tipizzata `RateLimitException` → `AnthropicServiceException` →
  `AnthropicIoException` con retry solo sui primi; mai lasciare l'utente senza messaggio.

## Verifica

1. `backend`: `./mvnw test` con test di servizio (parser, dedup, render PDF, mapping stati) e un
   test di integrazione Claude eseguito solo con `ANTHROPIC_API_KEY` presente.
2. `frontend`: `ng test` sui componenti Kanban e wizard; `ng build` senza warning.
3. Desktop: `jpackage` produce `JobPilot.exe`; doppio click apre la finestra entro pochi
   secondi, l'icona compare nella tray, "Esci" chiude il processo Java (verifica in Task Manager).
4. End-to-end manuale: incolla 3 annunci reali (1 LinkedIn, 1 Indeed, 1 stage) → analisi con
   punteggio sensato → PDF CV e lettera aperti e leggibili → "Apri annuncio" porta alla pagina
   giusta → stato INVIATA in dashboard → reminder dopo 7 giorni (simulato cambiando la data).
5. Controllo anti-invenzione: per 5 CV generati, ogni skill/esperienza nel PDF deve esistere nel
   profilo master (test automatico che confronta i termini).

## Primo passo dopo l'approvazione

Eseguire la Fase 0 per intero e la Fase 1 punti 1-3 (profilo, import, analisi AI), così da avere
entro la prima sessione un'offerta incollata che riceve un punteggio reale da Claude.
