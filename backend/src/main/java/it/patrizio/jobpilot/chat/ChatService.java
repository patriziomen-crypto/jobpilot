package it.patrizio.jobpilot.chat;

import it.patrizio.jobpilot.ai.AiException;
import it.patrizio.jobpilot.ai.AiProviderConfig;
import it.patrizio.jobpilot.ai.AiRouter;
import it.patrizio.jobpilot.ai.ChatMessage;
import it.patrizio.jobpilot.ai.ChatReply;
import it.patrizio.jobpilot.applications.ApplicationDto;
import it.patrizio.jobpilot.applications.ApplicationService;
import it.patrizio.jobpilot.applications.ApplicationStatus;
import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.documents.DocumentService;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.ProfileContext;
import it.patrizio.jobpilot.profile.ProfileService;
import it.patrizio.jobpilot.search.BrowserSessions;
import it.patrizio.jobpilot.search.BrowserSite;
import it.patrizio.jobpilot.search.JobSearchService;
import it.patrizio.jobpilot.search.SearchSettings;
import it.patrizio.jobpilot.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'assistente in chat dentro l'app: conosce la persona attiva, le sue candidature, i fornitori AI e gli
 * account collegati; risponde a domande, spiega come si usa JobPilot e può eseguire qualche azione
 * (cambiare stato, analizzare, generare documenti, avviare la ricerca, aprire pagine). Usa i fornitori AI
 * attivi nelle Impostazioni; senza fornitori dà risposte fisse di aiuto.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY = 20;
    private static final int MAX_APPLICATIONS_IN_CONTEXT = 60;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ITALIAN);

    private final ProfileContext profileContext;
    private final ProfileService profileService;
    private final ApplicationService applications;
    private final DocumentService documents;
    private final JobSearchService search;
    private final BrowserSessions browser;
    private final SettingsService settings;
    private final AiRouter ai;
    private final TransactionTemplate tx;

    private final Map<Long, List<ChatMessage>> conversations = new ConcurrentHashMap<>();

    public ChatService(ProfileContext profileContext, ProfileService profileService, ApplicationService applications,
                       DocumentService documents, JobSearchService search, BrowserSessions browser,
                       SettingsService settings, AiRouter ai, PlatformTransactionManager transactionManager) {
        this.tx = new TransactionTemplate(transactionManager);
        this.profileContext = profileContext;
        this.profileService = profileService;
        this.applications = applications;
        this.documents = documents;
        this.search = search;
        this.browser = browser;
        this.settings = settings;
        this.ai = ai;
    }

    public List<ChatMessage> history() {
        return List.copyOf(conversation(profileContext.currentProfileId()));
    }

    public void reset() {
        conversations.remove(profileContext.currentProfileId());
    }

    /** Invia un messaggio dell'utente e restituisce la risposta (con le azioni eseguite). */
    public ChatResponse send(String text) {
        if (text == null || text.isBlank()) throw JobPilotException.badRequest("Scrivi un messaggio");
        Long profileId = profileContext.currentProfileId();
        List<ChatMessage> conversation = conversation(profileId);
        List<ChatMessage> history;
        synchronized (conversation) {
            history = new ArrayList<>(conversation);
        }
        history.add(ChatMessage.user(text.strip()));
        trim(history);

        String system = systemPrompt(profileId);
        ChatReply reply;
        String provider;
        String model;
        try {
            AiRouter.AiResult<ChatReply> result = ai.chat(system, history);
            reply = result.value();
            provider = result.provider();
            model = result.model();
        } catch (AiException e) {
            log.info("Assistente senza AI: {}", e.getMessage());
            reply = new ChatReply(basicAnswer(text, e.getMessage()), List.of());
            provider = "Modalità base (senza AI)";
            model = "regole";
        }

        List<String> done = new ArrayList<>();
        String navigateTo = null;
        for (ChatReply.Action action : reply.safeActions()) {
            String outcome = execute(action);
            if (outcome == null) continue;
            if (outcome.startsWith("OPEN:")) navigateTo = outcome.substring(5);
            else done.add(outcome);
        }

        ChatMessage answer = ChatMessage.assistant(reply.reply() == null ? "" : reply.reply().strip());
        synchronized (conversation) {
            conversation.add(ChatMessage.user(text.strip()));
            conversation.add(answer);
            trim(conversation);
        }
        return new ChatResponse(answer, provider, model, done, navigateTo);
    }

    // ------------------------------------------------------------------ azioni

    private String execute(ChatReply.Action a) {
        String type = a.type() == null ? "" : a.type().strip().toUpperCase(Locale.ROOT);
        if (type.isBlank()) return null;
        try {
            switch (type) {
                case "CHANGE_STATUS" -> {
                    Long id = parseId(a.applicationId());
                    ApplicationStatus status = ApplicationStatus.valueOf(a.status().strip().toUpperCase(Locale.ROOT));
                    ApplicationDto dto = applications.changeStatus(id, status);
                    return "Candidatura #" + id + " (" + dto.job().title() + ") spostata in " + status;
                }
                case "ANALYZE" -> {
                    Long id = parseId(a.applicationId());
                    ApplicationDto dto = applications.analyze(id);
                    return "Candidatura #" + id + " analizzata: punteggio " + dto.analysis().score() + "/100";
                }
                case "GENERATE" -> {
                    Long id = parseId(a.applicationId());
                    documents.generate(id, true, true);
                    return "CV e lettera generati per la candidatura #" + id;
                }
                case "START_SEARCH" -> {
                    search.start();
                    return "Ricerca automatica avviata: segui l'avanzamento nella pagina Ricerca";
                }
                case "LOGIN" -> {
                    BrowserSite site = BrowserSite.parse(a.site() == null ? "" : a.site().strip());
                    browser.openLogin(site);
                    return "Finestra di accesso a " + site.label() + " aperta";
                }
                case "OPEN" -> {
                    String route = a.route() == null ? "" : a.route().strip();
                    if (!route.matches("/|/ricerca|/importa|/statistiche|/profilo|/impostazioni|/candidature/\\d+")) {
                        return null;
                    }
                    return "OPEN:" + route;
                }
                default -> {
                    return null;
                }
            }
        } catch (JobPilotException e) {
            return "Azione " + type + " non riuscita: " + e.getMessage();
        } catch (RuntimeException e) {
            log.warn("Azione dell'assistente fallita: {}", type, e);
            return "Azione " + type + " non riuscita: " + e.getMessage();
        }
    }

    private static Long parseId(String s) {
        try {
            return Long.parseLong(s.replaceAll("[^0-9]", ""));
        } catch (RuntimeException e) {
            throw JobPilotException.badRequest("numero di candidatura mancante");
        }
    }

    // ------------------------------------------------------------------ contesto

    private String systemPrompt(Long profileId) {
        // Il testo del profilo legge esperienze e competenze (lazy): va fatto dentro una transazione
        String profileText = tx.execute(status -> {
            Profile profile = profileService.getProfile(profileId);
            return profileService.toPromptText(profile);
        });
        StringBuilder sb = new StringBuilder();
        sb.append("""
                Sei l'assistente integrato in JobPilot, un'app desktop personale per gestire la ricerca di lavoro su
                LinkedIn e Indeed. Parli con la persona che la usa. Rispondi SEMPRE in italiano, in modo breve e concreto,
                con il tu. Testo semplice: niente tabelle né titoli; al massimo elenchi puntati con "-".
                Non inventare dati: usa solo le informazioni qui sotto. Se non sai una cosa, dillo.
                Quando citi una candidatura usa il numero (#id) e il titolo.
                Esegui un'azione solo se l'utente la chiede chiaramente; per le azioni che cambiano qualcosa
                (stato, analisi, documenti) conferma nel testo cosa stai facendo. Non puoi eliminare nulla.

                COME FUNZIONA JOBPILOT (guida per rispondere alle domande "come si fa"):
                - Dashboard (/): le candidature in colonne per stato (Trovate, Da valutare, Pronte, Inviate, Risposta, Colloquio,
                  Offerta, Rifiutate, Scadute); si trascinano tra le colonne. Clic su una card per aprirla.
                - Pagina candidatura (/candidature/<id>): passo 1 analisi (punteggio 0-100, requisiti soddisfatti/mancanti, red flag),
                  passo 2 "Genera CV e lettera" (PDF su misura, cartella output), passo 3 invio: "Apri annuncio" per candidarsi a mano
                  oppure "Compila la candidatura nel browser" (invio assistito: l'app apre Edge, compila il modulo e si ferma prima
                  di Invia; poi si preme "Ho inviato"). Note e data di promemoria in fondo.
                - Ricerca automatica (/ricerca): parole chiave una per riga, zona, giorni; interruttori LinkedIn/Indeed, "Analizza
                  subito", "Cerca da sola ogni mattina alle 8"; pulsante "Cerca ora". In alto "Account collegati": stato
                  connesso/non connesso a LinkedIn e Indeed, pulsanti Accedi / Disconnetti / Verifica adesso. Indeed senza
                  accesso mostra pochi annunci. LinkedIn serve solo per l'invio assistito.
                - Importa (/importa): incolla l'indirizzo o il testo di un annuncio per aggiungerlo a mano. C'è anche un bookmarklet.
                - Statistiche (/statistiche): totali, tasso di risposta, promemoria da ricontattare.
                - Profilo (/profilo): dati del CV master (esperienze, formazione, competenze) e "Risposte pronte" usate per
                  i moduli di candidatura.
                - Impostazioni (/impostazioni): fornitori AI. "Aggiungi fornitore AI" -> scegli il servizio (Claude a pagamento,
                  Groq e Gemini gratis con limiti, GitHub Models, OpenAI, Mistral, OpenRouter, Ollama locale) -> incolla la
                  chiave -> Salva -> "Prova". L'app usa il numero 1 e passa al successivo se non risponde; in fondo c'è sempre
                  la modalità base senza AI. Per Claude la chiave si crea su console.anthropic.com (Settings -> API keys) e
                  serve credito prepagato: l'abbonamento Claude.ai NON vale per le API. Interruttore "Invio automatico senza
                  conferma" (sconsigliato: rischio blocco account).
                - Persona in alto a destra: l'app gestisce più persone, ognuna con profilo, candidature e ricerca propri.
                - I PDF finiscono nella cartella output/<persona>/<data_azienda_ruolo>/ ("Apri cartella" nella candidatura).

                AZIONI CHE PUOI ESEGUIRE (campo actions): CHANGE_STATUS (applicationId + status), ANALYZE (applicationId),
                GENERATE (applicationId: CV e lettera), START_SEARCH, LOGIN (site linkedin|indeed), OPEN (route).
                Per "apri la candidatura 3" usa OPEN con route /candidature/3.

                """);
        sb.append("DATA DI OGGI: ").append(LocalDate.now()).append("\n\n");

        sb.append("PERSONA ATTIVA:\n").append(profileText).append("\n\n");

        sb.append("FORNITORI AI (in ordine di uso):\n");
        List<AiProviderConfig> providers = settings.aiProviders();
        if (providers.isEmpty()) sb.append("- nessuno: l'app è in modalità base\n");
        for (AiProviderConfig c : providers) {
            sb.append("- ").append(c.name()).append(" (modello ").append(c.model()).append(") ")
                    .append(c.enabled() ? "attivo" : "disattivato")
                    .append(c.lastStatus() == null ? "" : ", ultimo esito " + c.lastStatus()
                            + (c.lastMessage() == null ? "" : ": " + c.lastMessage()))
                    .append("\n");
        }
        sb.append("\n");

        BrowserSessions.Status b = browser.status();
        sb.append("ACCOUNT COLLEGATI NEL BROWSER DELL'APP: LinkedIn ").append(word(b.linkedin()))
                .append(", Indeed ").append(word(b.indeed()))
                .append(b.browserBusy() ? " (browser in uso: " + b.browserOwner() + ")" : "").append("\n\n");

        try {
            SearchSettings s = search.getSettings();
            sb.append("RICERCA AUTOMATICA: parole chiave ").append(s.keywords().isEmpty() ? "nessuna" : String.join(" | ", s.keywords()))
                    .append("; zona ").append(s.location()).append("; ultimi ").append(s.days()).append(" giorni; LinkedIn ")
                    .append(s.linkedin() ? "sì" : "no").append(", Indeed ").append(s.indeed() ? "sì" : "no")
                    .append("; programmata alle 8: ").append(s.scheduled() ? "sì" : "no")
                    .append("; ricerca in corso: ").append(search.status().running() ? "sì" : "no").append("\n\n");
        } catch (RuntimeException e) {
            log.debug("Impostazioni di ricerca non lette per il contesto: {}", e.getMessage());
        }

        List<ApplicationDto> apps = applications.findAll();
        Map<ApplicationStatus, Integer> counts = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationDto a : apps) counts.merge(a.status(), 1, Integer::sum);
        sb.append("CANDIDATURE DELLA PERSONA (").append(apps.size()).append(" in tutto");
        for (Map.Entry<ApplicationStatus, Integer> e : counts.entrySet()) {
            sb.append(", ").append(e.getValue()).append(" ").append(e.getKey());
        }
        sb.append("):\n");
        if (apps.isEmpty()) sb.append("- nessuna\n");
        int n = 0;
        for (ApplicationDto a : apps) {
            if (n++ >= MAX_APPLICATIONS_IN_CONTEXT) {
                sb.append("- ... e altre ").append(apps.size() - MAX_APPLICATIONS_IN_CONTEXT).append("\n");
                break;
            }
            sb.append("- #").append(a.id()).append(" | ").append(a.job().title()).append(" | ")
                    .append(a.job().company() == null ? "azienda ?" : a.job().company()).append(" | ")
                    .append(a.job().location() == null ? "" : a.job().location()).append(" | ")
                    .append(a.job().source()).append(" | stato ").append(a.status());
            if (a.analysis() != null) {
                sb.append(" | punteggio ").append(a.analysis().score()).append("/100");
                if (a.analysis().redFlags() != null && !a.analysis().redFlags().isEmpty()) {
                    sb.append(" | red flag: ").append(String.join(", ", a.analysis().redFlags()));
                }
            } else {
                sb.append(" | non analizzata");
            }
            sb.append(" | CV ").append(a.cvPath() == null ? "no" : "sì").append(", lettera ").append(a.letterPath() == null ? "no" : "sì");
            if (a.sentAt() != null) sb.append(" | inviata il ").append(a.sentAt().toLocalDate().format(DATE));
            if (a.followUpAt() != null) sb.append(" | promemoria ").append(a.followUpAt().format(DATE));
            if (a.notes() != null && !a.notes().isBlank()) sb.append(" | note: ").append(shorten(a.notes(), 120));
            sb.append("\n");
        }
        return sb.toString();
    }

    private static String word(BrowserSessions.SiteSession s) {
        if (s == null || !s.known()) return "da verificare";
        return s.loggedIn() ? "connesso" : "non connesso";
    }

    private static String shorten(String s, int max) {
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    // ------------------------------------------------------------------ senza AI

    /** Risposte fisse quando nessun fornitore AI risponde: almeno le istruzioni di base. */
    static String basicAnswer(String question, String why) {
        String q = question.toLowerCase(Locale.ROOT);
        String intro = "Al momento nessun fornitore AI risponde (" + why + "), quindi posso darti solo le istruzioni di base.\n\n";
        if (q.contains("claude") || q.contains("chiave") || q.contains("fornitore") || q.contains(" ai") || q.contains("groq")) {
            return intro + "Per collegare un'AI: Impostazioni (icona ingranaggio in alto a destra) → \"Aggiungi fornitore AI\" → scegli il servizio → incolla la chiave → Salva → \"Prova\".\n"
                    + "- Claude: chiave da console.anthropic.com (Settings → API keys); serve credito prepagato, l'abbonamento Claude.ai non vale per le API.\n"
                    + "- Groq e Gemini sono gratuiti con limiti giornalieri.";
        }
        if (q.contains("indeed") || q.contains("linkedin") || q.contains("acced") || q.contains("login") || q.contains("conness")) {
            return intro + "Gli accessi si gestiscono nella pagina Ricerca automatica, sezione \"Account collegati\": Accedi apre la finestra di Edge dell'app, Disconnetti cancella l'accesso, Verifica adesso ricontrolla lo stato.";
        }
        if (q.contains("invia") || q.contains("candidat") || q.contains("compil")) {
            return intro + "Apri una candidatura dalla dashboard: passo 1 analisi, passo 2 \"Genera CV e lettera\", passo 3 \"Compila la candidatura nel browser\" (l'app compila il modulo e si ferma prima di Invia) oppure \"Apri annuncio\" per farlo a mano. Alla fine premi \"Ho inviato\".";
        }
        if (q.contains("ricerc") || q.contains("cerca") || q.contains("annunci")) {
            return intro + "Pagina Ricerca automatica: scrivi le parole chiave (una per riga), la zona e i giorni, poi \"Cerca ora\". Con \"Cerca da sola ogni mattina alle 8\" lo fa in automatico quando l'app è aperta.";
        }
        return intro + "Posso spiegarti come collegare un'AI, come accedere a LinkedIn e Indeed, come cercare annunci e come inviare le candidature. Con un fornitore AI attivo rispondo anche a domande sulle tue candidature ed eseguo azioni (cambiare stato, analizzare, generare documenti, avviare la ricerca).";
    }

    // ------------------------------------------------------------------ interno

    private List<ChatMessage> conversation(Long profileId) {
        return conversations.computeIfAbsent(profileId, k -> new ArrayList<>());
    }

    /** Tiene gli ultimi messaggi e fa sì che la storia cominci sempre con l'utente (richiesto da Claude). */
    private static void trim(List<ChatMessage> list) {
        while (list.size() > MAX_HISTORY) list.remove(0);
        while (!list.isEmpty() && !"user".equals(list.get(0).role())) list.remove(0);
    }
}
