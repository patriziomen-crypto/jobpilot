package it.patrizio.jobpilot.search;

import it.patrizio.jobpilot.applications.ApplicationDto;
import it.patrizio.jobpilot.applications.ApplicationService;
import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.jobs.JobImportRequest;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.ProfileContext;
import it.patrizio.jobpilot.profile.ProfileSearch;
import it.patrizio.jobpilot.profile.ProfileSearchRepository;
import it.patrizio.jobpilot.profile.ProfileService;
import it.patrizio.jobpilot.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Esegue la ricerca automatica per una persona: per ogni parola chiave interroga LinkedIn e Indeed,
 * salta gli annunci che quella persona ha già, legge la descrizione dei nuovi, li importa come
 * candidature TROVATA e, se richiesto, li fa analizzare. Gira in un thread separato: il frontend ne legge lo stato.
 */
@Service
public class JobSearchService {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);
    private static final int MAX_LOG = 200;
    private static final int LINKEDIN_PAGE = 25;
    private static final int LINKEDIN_MAX_PAGES = 4;

    private final SettingsService settings;
    private final ProfileSearchRepository searches;
    private final ProfileService profileService;
    private final ProfileContext profileContext;
    private final LinkedInSearchClient linkedIn;
    private final ApplicationService applications;
    private final BrowserLock browserLock;
    private final BrowserSessions browserSessions;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    private final List<String> logLines = new ArrayList<>();
    private final Set<String> seenInRun = ConcurrentHashMap.newKeySet();
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile int found, imported, analyzed, errors;
    private volatile String current = "";
    private volatile Long profileId;
    private volatile String profileName = "";

    public JobSearchService(SettingsService settings, ProfileSearchRepository searches, ProfileService profileService,
                            ProfileContext profileContext, LinkedInSearchClient linkedIn, ApplicationService applications,
                            BrowserLock browserLock, BrowserSessions browserSessions) {
        this.settings = settings;
        this.browserSessions = browserSessions;
        this.searches = searches;
        this.profileService = profileService;
        this.profileContext = profileContext;
        this.linkedIn = linkedIn;
        this.applications = applications;
        this.browserLock = browserLock;
    }

    @Transactional
    public SearchSettings getSettings() {
        return SearchSettings.from(searchOf(profileContext.currentProfileId()));
    }

    @Transactional
    public SearchSettings updateSettings(SearchSettings s) {
        ProfileSearch entity = searchOf(profileContext.currentProfileId());
        s.applyTo(entity);
        return SearchSettings.from(searches.save(entity));
    }

    private ProfileSearch searchOf(Long id) {
        return searches.findByProfileId(id).orElseGet(() -> {
            ProfileSearch ps = new ProfileSearch();
            ps.setProfile(profileService.getProfile(id));
            return searches.save(ps);
        });
    }

    public synchronized SearchStatus status() {
        return new SearchStatus(running.get(), profileId, profileName, startedAt, finishedAt, found, imported, analyzed,
                errors, current, List.copyOf(logLines));
    }

    /** Avvia la ricerca per la persona attiva; errore se ce n'è già una in corso. */
    public void start() {
        start(profileContext.currentProfileId());
    }

    public void start(Long id) {
        if (!running.compareAndSet(false, true)) {
            throw new JobPilotException(HttpStatus.CONFLICT, "C'è già una ricerca in corso");
        }
        SearchSettings s;
        Profile profile;
        try {
            profile = profileService.getProfile(id);
            s = SearchSettings.from(searchOf(id));
            if (s.keywords().isEmpty()) {
                throw JobPilotException.badRequest("Inserisci almeno una parola chiave da cercare");
            }
            if (s.indeed()) {
                browserLock.acquire("ricerca su Indeed");
            }
        } catch (RuntimeException e) {
            running.set(false);
            throw e;
        }
        synchronized (this) {
            logLines.clear();
            found = imported = analyzed = errors = 0;
            startedAt = LocalDateTime.now();
            finishedAt = null;
            stopRequested.set(false);
            seenInRun.clear();
            profileId = id;
            profileName = profile.getFullName();
        }
        Thread thread = new Thread(() -> run(id, s), "ricerca-offerte");
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        stopRequested.set(true);
        say("Interruzione richiesta: finisco l'annuncio corrente e mi fermo");
    }

    /** Ricerca programmata ogni mattina alle 8 per tutte le persone che l'hanno attivata, una dopo l'altra. */
    @Scheduled(cron = "0 0 8 * * *")
    public void scheduledRun() {
        for (ProfileSearch ps : searches.findAllByScheduledTrue()) {
            Long id = ps.getProfile().getId();
            try {
                waitUntilIdle(2 * 60 * 60 * 1000L);
                log.info("Ricerca programmata per il profilo {}", id);
                start(id);
                waitUntilIdle(2 * 60 * 60 * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("Ricerca programmata non avviata per il profilo {}: {}", id, e.getMessage());
            }
        }
    }

    private void waitUntilIdle(long maxMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + maxMillis;
        while (running.get() && System.currentTimeMillis() < deadline) {
            Thread.sleep(5000);
        }
    }

    private void run(Long id, SearchSettings s) {
        try {
            say("Ricerca per " + profileName + ": " + s.keywords().size() + " parole chiave, zona \"" + s.location()
                    + "\", ultimi " + s.days() + " giorni");
            if (s.linkedin()) {
                runLinkedIn(id, s);
            }
            if (s.indeed() && !stopRequested.get()) {
                runIndeed(id, s);
            }
            say("Fine: " + found + " annunci visti, " + imported + " nuovi importati, " + analyzed + " analizzati, " + errors + " errori");
        } catch (Exception e) {
            log.error("Ricerca fallita", e);
            say("Errore: " + e.getMessage());
            errors++;
        } finally {
            current = "";
            finishedAt = LocalDateTime.now();
            if (s.indeed()) browserLock.release();
            running.set(false);
        }
    }

    private void runLinkedIn(Long id, SearchSettings s) {
        for (String keyword : s.keywords()) {
            if (stopRequested.get()) return;
            current = "LinkedIn: " + keyword;
            int taken = 0;
            try {
                // Più pagine di risultati: dopo la prima ricerca i primi 25 sono quasi tutti già noti
                for (int pagina = 0; pagina < LINKEDIN_MAX_PAGES && taken < s.maxPerQuery() && !stopRequested.get(); pagina++) {
                    List<JobCard> cards = linkedIn.search(keyword, s.location(), s.days(), s.remote(), pagina * LINKEDIN_PAGE);
                    say("LinkedIn \"" + keyword + "\" pagina " + (pagina + 1) + ": " + cards.size() + " risultati");
                    if (cards.isEmpty()) break;
                    for (JobCard card : cards) {
                        if (stopRequested.get() || taken >= s.maxPerQuery()) break;
                        found++;
                        if (applications.alreadyHas(card.url(), id) || duplicateInRun(card)) continue;
                        taken++;
                        current = "LinkedIn: " + card.title() + " (" + card.company() + ")";
                        String description = linkedIn.description(card.url());
                        importCard(id, card, description, s.autoAnalyze());
                        pause(1500);
                    }
                    if (cards.size() < LINKEDIN_PAGE) break;
                    pause(2000);
                }
            } catch (Exception e) {
                errors++;
                say("LinkedIn \"" + keyword + "\": " + e.getMessage());
            }
        }
    }

    private void runIndeed(Long id, SearchSettings s) {
        current = "Indeed: apro il browser";
        say("Indeed: apro Microsoft Edge (finestra dedicata)");
        Path profile = settings.file().getParent().resolve("browser-profile");
        try (IndeedSearchClient indeed = new IndeedSearchClient(profile, false, this::say, browserSessions)) {
            if (!browserSessions.isLoggedIn(BrowserSite.INDEED)) {
                say("Indeed: non risulti connesso. Senza accesso vedrai pochi annunci: usa \"Accedi\" nella sezione Account collegati");
            }
            for (String keyword : s.keywords()) {
                if (stopRequested.get()) return;
                current = "Indeed: " + keyword;
                try {
                    List<JobCard> cards = indeed.search(keyword, s.location(), s.days(), 50);
                    say("Indeed \"" + keyword + "\": " + cards.size() + " risultati");
                    int taken = 0;
                    for (JobCard card : cards) {
                        if (stopRequested.get() || taken >= s.maxPerQuery()) break;
                        found++;
                        if (applications.alreadyHas(card.url(), id) || duplicateInRun(card)) continue;
                        if (!relevant(card, keyword)) {
                            say("Salto (fuori tema): " + card.title());
                            continue;
                        }
                        taken++;
                        current = "Indeed: " + card.title() + " (" + card.company() + ")";
                        String description = indeed.description(card.url());
                        importCard(id, card, description, s.autoAnalyze());
                    }
                } catch (Exception e) {
                    errors++;
                    say("Indeed \"" + keyword + "\": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors++;
            say("Indeed: impossibile avviare il browser (" + e.getMessage() + ")");
            log.error("Avvio browser Indeed fallito", e);
        }
    }

    /**
     * Indeed mette in cima annunci sponsorizzati fuori tema: teniamo solo quelli il cui titolo contiene
     * una parola significativa (almeno 5 lettere) della ricerca.
     */
    static boolean relevant(JobCard card, String keyword) {
        if (card.title() == null) return false;
        String title = card.title().toLowerCase();
        for (String w : keyword.toLowerCase().split("\\s+")) {
            if (w.length() >= 5 && title.contains(w)) return true;
        }
        return false;
    }

    private boolean duplicateInRun(JobCard card) {
        if (card.title() == null && card.company() == null) return false;
        String key = (card.title() == null ? "" : card.title().toLowerCase().strip()) + "|"
                + (card.company() == null ? "" : card.company().toLowerCase().strip());
        return !seenInRun.add(key);
    }

    private void importCard(Long id, JobCard card, String description, boolean autoAnalyze) {
        try {
            String text = description != null ? description
                    : "(Descrizione non letta automaticamente: apri l'annuncio e incolla il testo nelle note o reimporta.)";
            ApplicationDto app = applications.importAndCreate(new JobImportRequest(
                    card.url(), text, card.title(), card.company(), card.location(), null), id);
            imported++;
            say("Nuova: " + card.title() + " — " + (card.company() == null ? "?" : card.company())
                    + (description == null ? " (senza descrizione)" : ""));
            if (autoAnalyze && description != null) {
                current = "Analisi: " + card.title();
                try {
                    ApplicationDto analyzedApp = applications.analyze(app.id(), id);
                    this.analyzed++;
                    say("  → punteggio " + analyzedApp.analysis().score() + "/100 (" + analyzedApp.analysis().provider() + ")");
                } catch (Exception e) {
                    errors++;
                    say("  → analisi fallita: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            errors++;
            say("Import fallito per " + card.url() + ": " + e.getMessage());
        }
    }

    private synchronized void say(String message) {
        String line = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + message;
        logLines.add(line);
        if (logLines.size() > MAX_LOG) logLines.remove(0);
        log.info(message);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis + (long) (Math.random() * millis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
