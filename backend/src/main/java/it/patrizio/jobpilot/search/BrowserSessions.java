package it.patrizio.jobpilot.search;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.Cookie;
import it.patrizio.jobpilot.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Accessi memorizzati nel browser dell'app (Edge con profilo dedicato): sa se l'utente è connesso a
 * LinkedIn e a Indeed leggendo i cookie del profilo, apre UNA finestra di accesso valida per entrambi
 * i siti (ogni sito in una scheda), permette di disconnettersi e ricorda l'ultimo esito tra un avvio e l'altro.
 */
@Component
public class BrowserSessions {

    private static final Logger log = LoggerFactory.getLogger(BrowserSessions.class);
    private static final long LOGIN_WINDOW_MS = 15 * 60 * 1000L;
    private static final int MAX_LOG = 60;

    /** Stato di un sito: known=false finché non è mai stato verificato. */
    public record SiteSession(boolean loggedIn, boolean known, LocalDateTime checkedAt, String message) {
        static SiteSession unknown() {
            return new SiteSession(false, false, null, "Mai verificato");
        }
    }

    public record Status(SiteSession linkedin, SiteSession indeed, boolean loginWindowOpen, boolean browserBusy,
                         String browserOwner, List<String> log) {}

    private final SettingsService settings;
    private final BrowserLock lock;
    private final Map<BrowserSite, SiteSession> sessions = new EnumMap<>(BrowserSite.class);
    private final List<String> logLines = new ArrayList<>();
    private final ConcurrentLinkedQueue<BrowserSite> pendingLogins = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean loginWindowOpen = new AtomicBoolean(false);

    public BrowserSessions(SettingsService settings, BrowserLock lock) {
        this.settings = settings;
        this.lock = lock;
        for (BrowserSite site : BrowserSite.values()) {
            sessions.put(site, loadPersisted(site));
        }
    }

    public synchronized Status status() {
        return new Status(sessions.get(BrowserSite.LINKEDIN), sessions.get(BrowserSite.INDEED), loginWindowOpen.get(),
                lock.isBusy(), lock.owner(), List.copyOf(logLines));
    }

    public synchronized boolean isLoggedIn(BrowserSite site) {
        return sessions.get(site).loggedIn();
    }

    // ------------------------------------------------------------------ finestra di accesso

    /**
     * Apre la pagina di accesso del sito nella finestra dedicata. Se la finestra è già aperta
     * (per l'altro sito), aggiunge una scheda invece di rifiutare.
     */
    public void openLogin(BrowserSite site) {
        if (loginWindowOpen.get()) {
            pendingLogins.add(site);
            say("Apro " + site.label() + " in una nuova scheda della finestra già aperta");
            return;
        }
        lock.acquire("finestra di accesso");
        loginWindowOpen.set(true);
        pendingLogins.add(site);
        Thread thread = new Thread(this::runLoginWindow, "finestra-accesso");
        thread.setDaemon(true);
        thread.start();
    }

    private void runLoginWindow() {
        try (Playwright playwright = Playwright.create()) {
            BrowserContext context = LoginWindow.launch(playwright, profileDir(), false);
            Page first = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
            boolean firstUsed = false;
            say("Finestra del browser aperta: accedi con il tuo account, poi chiudi la finestra");
            long deadline = System.currentTimeMillis() + LOGIN_WINDOW_MS;
            long lastCheck = 0;
            while (System.currentTimeMillis() < deadline) {
                BrowserSite next;
                while ((next = pendingLogins.poll()) != null) {
                    try {
                        Page page = firstUsed ? context.newPage() : first;
                        firstUsed = true;
                        page.navigate(next.loginUrl());
                        page.bringToFront();
                        say("Pagina di accesso a " + next.label() + " aperta");
                    } catch (Exception e) {
                        say("Non riesco ad aprire " + next.label() + ": " + e.getMessage());
                    }
                }
                try {
                    // cookies() è una chiamata al browser: se la finestra è stata chiusa lancia un errore
                    if (System.currentTimeMillis() - lastCheck > 2000) {
                        updateFromContext(context);
                        lastCheck = System.currentTimeMillis();
                    }
                    List<Page> open = context.pages();
                    if (open.isEmpty()) break;
                    open.get(0).waitForTimeout(700);
                } catch (Exception closed) {
                    break;
                }
            }
            say("Finestra di accesso chiusa");
            try { context.close(); } catch (Exception ignored) { }
        } catch (Exception e) {
            log.warn("Finestra di accesso", e);
            say("Finestra di accesso: " + e.getMessage());
        } finally {
            pendingLogins.clear();
            loginWindowOpen.set(false);
            lock.release();
        }
    }

    // ------------------------------------------------------------------ verifica e disconnessione

    /** Verifica gli accessi aprendo il browser in modo invisibile (qualche secondo). */
    public void check() {
        lock.acquire("verifica accessi");
        Thread thread = new Thread(() -> {
            try (Playwright playwright = Playwright.create()) {
                BrowserContext context = LoginWindow.launch(playwright, profileDir(), true);
                updateFromContext(context);
                say("Verifica fatta: LinkedIn " + word(BrowserSite.LINKEDIN) + ", Indeed " + word(BrowserSite.INDEED));
                try { context.close(); } catch (Exception ignored) { }
            } catch (Exception e) {
                log.warn("Verifica accessi fallita", e);
                say("Verifica non riuscita: " + e.getMessage());
            } finally {
                lock.release();
            }
        }, "verifica-accessi");
        thread.setDaemon(true);
        thread.start();
    }

    /** Cancella i cookie del sito dal profilo dedicato: l'app non è più connessa a quel sito. */
    public void logout(BrowserSite site) {
        lock.acquire("disconnessione da " + site.label());
        Thread thread = new Thread(() -> {
            try (Playwright playwright = Playwright.create()) {
                BrowserContext context = LoginWindow.launch(playwright, profileDir(), true);
                context.clearCookies(new BrowserContext.ClearCookiesOptions().setDomain(site.cookieDomain()));
                set(site, false, "Disconnesso da JobPilot");
                say("Disconnesso da " + site.label());
                try { context.close(); } catch (Exception ignored) { }
            } catch (Exception e) {
                log.warn("Disconnessione fallita", e);
                say("Disconnessione da " + site.label() + " non riuscita: " + e.getMessage());
            } finally {
                lock.release();
            }
        }, "disconnessione");
        thread.setDaemon(true);
        thread.start();
    }

    /** Chiamato da ricerca e invio assistito quando il sito chiede di nuovo l'accesso. */
    public void markLoggedOut(BrowserSite site, String why) {
        set(site, false, why);
    }

    /**
     * Legge i cookie del profilo e aggiorna lo stato di entrambi i siti. Va chiamato dal thread che
     * possiede il contesto Playwright (ricerca, invio, finestra di accesso).
     */
    public void updateFromContext(BrowserContext context) {
        for (BrowserSite site : BrowserSite.values()) {
            List<Cookie> cookies = context.cookies(site.cookieUrl());
            boolean in = cookies.stream().anyMatch(c -> site.authCookies().contains(c.name) && c.value != null && !c.value.isBlank());
            set(site, in, in ? "Accesso memorizzato nel browser dell'app" : "Nessun accesso memorizzato");
        }
    }

    /** All'avvio dell'app desktop verifica gli accessi in sottofondo, così la pagina mostra subito lo stato. */
    @EventListener(ApplicationReadyEvent.class)
    public void checkOnStartup() {
        if (!"true".equalsIgnoreCase(System.getProperty("jobpilot.desktop"))) return;
        try {
            check();
        } catch (RuntimeException e) {
            log.info("Verifica accessi all'avvio saltata: {}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ interno

    private synchronized void set(BrowserSite site, boolean loggedIn, String message) {
        SiteSession before = sessions.get(site);
        SiteSession now = new SiteSession(loggedIn, true, LocalDateTime.now().withNano(0), message);
        sessions.put(site, now);
        boolean changed = before == null || !before.known() || before.loggedIn() != loggedIn
                || before.checkedAt() == null || before.checkedAt().plusMinutes(10).isBefore(now.checkedAt());
        if (changed) {
            if (before != null && before.known() && before.loggedIn() != loggedIn) {
                say(site.label() + ": " + (loggedIn ? "connesso" : "non connesso"));
            }
            persist(site, now);
        }
    }

    private void persist(BrowserSite site, SiteSession s) {
        String k = "browser." + site.name().toLowerCase() + ".";
        settings.set(k + "loggedIn", String.valueOf(s.loggedIn()));
        settings.set(k + "checkedAt", s.checkedAt() == null ? null : s.checkedAt().toString());
        settings.set(k + "message", s.message());
        try {
            settings.save();
        } catch (RuntimeException e) {
            log.debug("Stato accessi non salvato: {}", e.getMessage());
        }
    }

    private SiteSession loadPersisted(BrowserSite site) {
        String k = "browser." + site.name().toLowerCase() + ".";
        String checked = settings.get(k + "checkedAt");
        if (checked == null) return SiteSession.unknown();
        try {
            return new SiteSession(settings.getBoolean(k + "loggedIn"), true, LocalDateTime.parse(checked),
                    settings.get(k + "message"));
        } catch (RuntimeException e) {
            return SiteSession.unknown();
        }
    }

    private String word(BrowserSite site) {
        return isLoggedIn(site) ? "connesso" : "non connesso";
    }

    private Path profileDir() {
        return settings.file().getParent().resolve("browser-profile");
    }

    private synchronized void say(String message) {
        logLines.add(LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss")) + "  " + message);
        if (logLines.size() > MAX_LOG) logLines.remove(0);
        log.info("[accessi] {}", message);
    }
}
