package it.patrizio.jobpilot.apply;

import it.patrizio.jobpilot.ai.AiRouter;
import it.patrizio.jobpilot.applications.Application;
import it.patrizio.jobpilot.applications.ApplicationRepository;
import it.patrizio.jobpilot.applications.ApplicationStatus;
import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.jobs.JobSource;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.ProfileContext;
import it.patrizio.jobpilot.profile.ProfileService;
import it.patrizio.jobpilot.search.BrowserLock;
import it.patrizio.jobpilot.search.BrowserSessions;
import it.patrizio.jobpilot.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Invio assistito: apre l'annuncio nel browser dell'app, compila il modulo e si ferma prima di "Invia"
 * (oppure invia da solo se l'utente ha attivato l'invio automatico nelle impostazioni).
 * Una candidatura alla volta; la finestra resta aperta finché l'utente non conferma o chiude.
 */
@Service
public class ApplyService {

    private static final Logger log = LoggerFactory.getLogger(ApplyService.class);
    public static final String AUTO_SUBMIT = "apply.auto-submit";
    private static final long WINDOW_TIMEOUT_MS = 20 * 60 * 1000L;

    private final ApplicationRepository applications;
    private final ProfileService profileService;
    private final ProfileContext profileContext;
    private final SettingsService settings;
    private final AiRouter ai;
    private final BrowserLock browserLock;
    private final BrowserSessions browserSessions;
    private final TransactionTemplate tx;

    private volatile ApplyBot bot;
    private volatile boolean running;
    private volatile Long applicationId;
    private volatile String jobTitle = "";
    private volatile ApplyOutcome outcome;
    private volatile LocalDateTime startedAt, finishedAt;
    private final List<String> logLines = new ArrayList<>();

    public ApplyService(ApplicationRepository applications, ProfileService profileService, ProfileContext profileContext,
                        SettingsService settings, AiRouter ai, BrowserLock browserLock, BrowserSessions browserSessions,
                        PlatformTransactionManager tm) {
        this.applications = applications;
        this.browserSessions = browserSessions;
        this.profileService = profileService;
        this.profileContext = profileContext;
        this.settings = settings;
        this.ai = ai;
        this.browserLock = browserLock;
        this.tx = new TransactionTemplate(tm);
    }

    public boolean autoSubmit() {
        return settings.getBoolean(AUTO_SUBMIT);
    }

    public void setAutoSubmit(boolean value) {
        settings.set(AUTO_SUBMIT, String.valueOf(value));
        settings.save();
    }

    public synchronized ApplyStatus status() {
        ApplyBot b = bot;
        return new ApplyStatus(running, b != null && b.isWindowOpen(), applicationId, jobTitle, outcome, startedAt, finishedAt,
                b == null ? List.of() : List.copyOf(b.unanswered()),
                b == null ? List.of() : List.copyOf(b.answeredLog()),
                List.copyOf(logLines));
    }

    /** Avvia l'invio assistito per una candidatura della persona attiva. */
    public void start(Long id) {
        Long profileId = profileContext.currentProfileId();
        record Input(Long id, JobSource source, String url, String title, String cvPath, Profile profile, String profileText) {}
        Input in = tx.execute(s -> {
            Application app = applications.findByIdAndProfileId(id, profileId)
                    .orElseThrow(() -> JobPilotException.notFound("Candidatura " + id + " non trovata"));
            if (app.getJobOffer().getUrl() == null) {
                throw JobPilotException.badRequest("Questa offerta non ha un indirizzo web: candidati manualmente");
            }
            Profile p = profileService.getProfile(profileId);
            p.getStandardAnswers().size();
            return new Input(app.getId(), app.getJobOffer().getSource(), app.getJobOffer().getUrl(), app.getJobOffer().getTitle(),
                    app.getCvPath(), p, profileService.toPromptText(p));
        });

        if (running) {
            throw new JobPilotException(HttpStatus.CONFLICT, "C'è già un invio assistito in corso: concludilo o chiudi la finestra");
        }
        closeBot();
        browserLock.acquire("invio assistito");
        synchronized (this) {
            running = true;
            applicationId = in.id();
            jobTitle = in.title();
            outcome = null;
            startedAt = LocalDateTime.now();
            finishedAt = null;
            logLines.clear();
        }
        boolean auto = autoSubmit();
        Thread thread = new Thread(() -> run(in.id(), in.source(), in.url(), in.cvPath(), in.profile(), in.profileText(), auto), "invio-assistito");
        thread.setDaemon(true);
        thread.start();
    }

    private void run(Long id, JobSource source, String url, String cvPath, Profile profile, String profileText, boolean auto) {
        Path profileDir = settings.file().getParent().resolve("browser-profile");
        try {
            say("Apro il browser dell'app su: " + url);
            bot = new ApplyBot(profileDir, profile, (question, options) -> ai.shortAnswer(profile, profileText, question, options),
                    this::say, browserSessions);
            ApplyOutcome result = bot.apply(source, url, cvPath, auto);
            outcome = result;
            if (result == ApplyOutcome.SUBMITTED) {
                markSent(id);
                say("Segnata come inviata nella dashboard");
                closeBot();
            } else if (result == ApplyOutcome.FAILED || result == ApplyOutcome.LOGIN_REQUIRED) {
                say("Puoi chiudere la finestra oppure completare a mano");
                waitForWindow();
            } else {
                say("La finestra resta aperta: quando hai inviato, premi \"Ho inviato\" nell'app");
                waitForWindow();
            }
        } catch (Exception e) {
            log.error("Invio assistito fallito", e);
            outcome = ApplyOutcome.FAILED;
            say("Errore: " + e.getMessage());
            closeBot();
        } finally {
            finishedAt = LocalDateTime.now();
            running = false;
            browserLock.release();
        }
    }

    /** Tiene vivo il bot finché la finestra è aperta e nessuno conferma (max 20 minuti). */
    private void waitForWindow() {
        long deadline = System.currentTimeMillis() + WINDOW_TIMEOUT_MS;
        while (bot != null && bot.isWindowOpen() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(1500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        closeBot();
    }

    /** L'utente dice di aver inviato: chiude la finestra e segna la candidatura come inviata. */
    public void confirmSent() {
        Long id = applicationId;
        if (id == null) throw JobPilotException.badRequest("Nessun invio assistito da confermare");
        markSent(id);
        say("Confermato dall'utente: candidatura inviata");
        closeBot();
    }

    /** L'utente rinuncia: chiude la finestra senza cambiare stato. */
    public void cancel() {
        say("Annullato dall'utente");
        closeBot();
    }

    private void markSent(Long id) {
        tx.execute(s -> {
            applications.findById(id).ifPresent(app -> {
                if (app.getSentAt() == null) {
                    app.setSentAt(LocalDateTime.now());
                    app.setFollowUpAt(LocalDate.now().plusDays(7));
                }
                app.setStatus(ApplicationStatus.INVIATA);
            });
            return null;
        });
    }

    private void closeBot() {
        ApplyBot b = bot;
        if (b != null) {
            try { b.close(); } catch (Exception ignored) { }
        }
        bot = null;
    }

    private synchronized void say(String message) {
        logLines.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + message);
        if (logLines.size() > 100) logLines.remove(0);
        log.info("[invio] {}", message);
    }
}
