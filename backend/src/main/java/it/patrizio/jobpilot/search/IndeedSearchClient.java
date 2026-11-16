package it.patrizio.jobpilot.search;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import it.patrizio.jobpilot.jobs.JobSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Ricerca su Indeed con un vero browser (Microsoft Edge pilotato da Playwright), perché Indeed
 * blocca le richieste dirette e, per leggere gli annunci, richiede un account.
 * Usa un profilo dedicato in %LOCALAPPDATA%\JobPilot\browser-profile: l'accesso a Indeed va fatto
 * una volta sola in quella finestra (pulsante "Accedi a Indeed") e poi resta memorizzato.
 * Le descrizioni vengono lette dal pannello laterale della pagina dei risultati, cliccando ogni scheda.
 * Un'istanza per ogni ricerca: va chiusa con close().
 */
public class IndeedSearchClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(IndeedSearchClient.class);
    /** Vecchio layout (#jobDescriptionText) e nuovo layout Indeed 2026 (viewjob-job-content, usato anche nel pannello laterale). */
    private static final String DESCRIPTION_SELECTOR =
            "#jobDescriptionText, [data-testid=jobDescriptionText], [data-testid=viewjob-job-content], #viewjob-job-content, .jobsearch-JobComponent-description";

    private final Playwright playwright;
    private final BrowserContext context;
    private final Page page;
    private final Consumer<String> progress;
    /** Facoltativo: aggiorna lo stato "connesso / non connesso" mostrato nella pagina Ricerca. */
    private final BrowserSessions sessions;

    public IndeedSearchClient(Path profileDir, boolean headless, Consumer<String> progress, BrowserSessions sessions) {
        this.progress = progress;
        this.sessions = sessions;
        this.playwright = Playwright.create();
        try {
            this.context = launch(playwright, profileDir, headless);
            this.page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
            this.page.setDefaultTimeout(30_000);
            refreshSessions();
        } catch (RuntimeException e) {
            // il browser non è partito: chiudiamo il driver Playwright per non lasciare processi appesi
            try { playwright.close(); } catch (Exception ignored) { }
            throw e;
        }
    }

    private static BrowserContext launch(Playwright playwright, Path profileDir, boolean headless) {
        return LoginWindow.launch(playwright, profileDir, headless);
    }

    public List<JobCard> search(String keywords, String location, int days, int max) {
        String url = "https://it.indeed.com/jobs?q=" + enc(keywords) + "&l=" + enc(location)
                + "&fromage=" + Math.max(1, days); // ordinamento per rilevanza: con "per data" dominano gli sponsorizzati fuori tema
        page.navigate(url);
        waitForHuman();
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        try {
            page.waitForSelector("a[data-jk], .no_results, #mosaic-provider-jobcards", new Page.WaitForSelectorOptions().setTimeout(20_000));
        } catch (Exception e) {
            log.warn("Indeed: nessuna lista risultati visibile ({})", page.title());
        }
        Map<String, JobCard> cards = new LinkedHashMap<>();
        for (ElementHandle link : page.querySelectorAll("a[data-jk]")) {
            String jk = link.getAttribute("data-jk");
            if (jk == null || jk.isBlank() || cards.containsKey(jk)) continue;
            ElementHandle card = link.evaluateHandle("el => el.closest('.job_seen_beacon, .cardOutline, .result') || el").asElement();
            String title = textOf(link, "span[title]");
            if (title == null) title = link.innerText();
            if ((title == null || title.isBlank()) && link.getAttribute("aria-label") != null) {
                title = link.getAttribute("aria-label").replaceFirst("(?i)^dettagli completi di\\s*", "");
            }
            String company = card == null ? null : textOf(card, "[data-testid=company-name]", ".companyName", ".company");
            String loc = card == null ? null : textOf(card, "[data-testid=text-location]", ".companyLocation", ".location");
            cards.put(jk, new JobCard(JobSource.INDEED, "https://it.indeed.com/viewjob?jk=" + jk,
                    title == null ? null : title.strip(), company, loc));
            if (cards.size() >= max) break;
        }
        log.info("Indeed \"{}\" @ {}: {} risultati", keywords, location, cards.size());
        return new ArrayList<>(cards.values());
    }

    /** Clicca la scheda nella pagina dei risultati e legge la descrizione dal pannello laterale; null se non trovata. */
    public String description(String jobUrl) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("jk=([A-Za-z0-9]{8,32})").matcher(jobUrl);
        if (!m.find()) {
            log.warn("Indeed: URL senza identificativo valido: {}", jobUrl);
            return null;
        }
        String jk = m.group(1);
        try {
            ElementHandle link = page.querySelector("a[data-jk='" + jk + "']");
            if (link != null) {
                // Il pannello laterale è lo stesso per tutte le schede: aspetto che il testo CAMBI rispetto a prima
                String before = currentDescription();
                link.click();
                String text = waitForDescription(12_000, before);
                if (text != null) return text;
            }
            // Pannello non disponibile: provo la pagina dell'annuncio (può chiedere l'accesso)
            page.navigate(jobUrl);
            waitForHuman();
            String text = waitForDescription(15_000, null);
            if (text == null) {
                log.info("Indeed: descrizione non trovata nella pagina {} (titolo: {})", jobUrl, page.title());
            }
            // Torno ai risultati per le schede successive
            page.goBack();
            return text;
        } catch (Exception e) {
            log.warn("Indeed: descrizione non letta per {}: {}", jobUrl, e.getMessage());
            return null;
        } finally {
            try {
                page.waitForTimeout(1500 + (long) (Math.random() * 1500)); // ritmo umano
            } catch (Exception ignored) {
                // finestra chiusa dall'utente: il risultato già letto resta valido
            }
        }
    }

    /** Testo della descrizione attualmente visibile (pagina o frame), o null. */
    private String currentDescription() {
        for (Frame frame : page.frames()) {
            try {
                ElementHandle el = frame.querySelector(DESCRIPTION_SELECTOR);
                if (el != null) {
                    String text = el.innerText();
                    if (text != null && text.strip().length() > 40) return text.strip();
                }
            } catch (Exception ignored) {
                // frame in navigazione
            }
        }
        return null;
    }

    /** Aspetta una descrizione diversa da "previous" (se indicata), entro il tempo dato. */
    private String waitForDescription(long timeoutMs, String previous) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (waitForHuman()) { deadline = System.currentTimeMillis() + timeoutMs; }
            String text = currentDescription();
            if (text != null && (previous == null || !text.equals(previous))) return text;
            page.waitForTimeout(500);
        }
        return null;
    }

    /**
     * Se compare una verifica anti-bot o la pagina di accesso, avvisa e aspetta (fino a 3 minuti)
     * che l'utente intervenga nella finestra. Restituisce true se ha dovuto aspettare.
     */
    private boolean waitForHuman() {
        long deadline = System.currentTimeMillis() + 180_000;
        boolean waited = false;
        while (System.currentTimeMillis() < deadline) {
            String title = page.title() == null ? "" : page.title().toLowerCase();
            String url = page.url() == null ? "" : page.url();
            boolean login = url.contains("secure.indeed.com/auth") || url.contains("/account/login");
            // Solo i titoli esatti delle pagine di verifica, non parole generiche che possono stare in un annuncio
            boolean challenge = title.startsWith("just a moment") || title.startsWith("ci siamo quasi")
                    || title.startsWith("security check") || title.startsWith("ulteriore verifica")
                    || page.querySelector("#challenge-running, .cf-turnstile, #px-captcha") != null;
            if (!login && !challenge) {
                if (waited) refreshSessions();
                return waited;
            }
            if (!waited) {
                progress.accept(login
                        ? "Indeed chiede l'accesso: entra con il tuo account nella finestra di Edge (basta una volta)"
                        : "Indeed chiede una verifica: completala nella finestra di Edge");
                if (login && sessions != null) sessions.markLoggedOut(BrowserSite.INDEED, "Indeed ha chiesto di nuovo l'accesso");
                waited = true;
            }
            page.waitForTimeout(2000);
        }
        return waited;
    }

    private void refreshSessions() {
        if (sessions == null) return;
        try {
            sessions.updateFromContext(context);
        } catch (Exception e) {
            log.debug("Stato accessi non aggiornato: {}", e.getMessage());
        }
    }

    private static String textOf(ElementHandle root, String... selectors) {
        for (String s : selectors) {
            ElementHandle el = root.querySelector(s);
            if (el != null) {
                String t = el.getAttribute("title");
                if (t == null || t.isBlank()) t = el.innerText();
                if (t != null && !t.isBlank()) return t.strip();
            }
        }
        return null;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        try { context.close(); } catch (Exception ignored) { }
        try { playwright.close(); } catch (Exception ignored) { }
    }
}
