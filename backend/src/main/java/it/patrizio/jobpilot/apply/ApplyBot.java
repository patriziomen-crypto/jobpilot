package it.patrizio.jobpilot.apply;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.ElementHandle;
import com.microsoft.playwright.Frame;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import it.patrizio.jobpilot.jobs.JobSource;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.search.BrowserSessions;
import it.patrizio.jobpilot.search.BrowserSite;
import it.patrizio.jobpilot.search.LoginWindow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * Compila la candidatura nel browser dell'app: LinkedIn "Candidatura semplice" e Indeed "Candidati ora".
 * Strategia: apre l'annuncio, clicca il pulsante di candidatura, e per ogni passaggio del modulo compila
 * i campi che riconosce (CV, telefono, domande) usando le risposte pronte del profilo e, per le domande
 * sconosciute, l'AI. Si ferma prima dell'invio finale, a meno che l'invio automatico non sia attivo.
 * La finestra resta aperta finché l'utente non chiude o conferma dall'app.
 */
public class ApplyBot implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ApplyBot.class);
    private static final int MAX_STEPS = 12;

    private final Playwright playwright;
    private final BrowserContext context;
    private final Page page;
    private final Consumer<String> progress;
    private final Profile profile;
    private final AnswerMatcher matcher;
    /** Risposta AI per le domande non coperte dalle risposte pronte: (domanda, opzioni) → risposta o null. */
    private final BiFunction<String, List<String>, String> aiAnswer;
    private final List<String> unanswered = new ArrayList<>();
    private final List<String> answeredLog = new ArrayList<>();
    /** Facoltativo: stato "connesso / non connesso" dei siti, aggiornato quando il sito chiede l'accesso. */
    private final BrowserSessions sessions;

    public ApplyBot(Path profileDir, Profile profile, BiFunction<String, List<String>, String> aiAnswer, Consumer<String> progress,
                    BrowserSessions sessions) {
        this.profile = profile;
        this.matcher = new AnswerMatcher(profile);
        this.aiAnswer = aiAnswer;
        this.progress = progress;
        this.sessions = sessions;
        this.playwright = Playwright.create();
        try {
            this.context = LoginWindow.launch(playwright, profileDir, false);
            this.page = context.pages().isEmpty() ? context.newPage() : context.pages().get(0);
            this.page.setDefaultTimeout(20_000);
            if (sessions != null) {
                try { sessions.updateFromContext(context); } catch (Exception ignored) { }
            }
        } catch (RuntimeException e) {
            try { playwright.close(); } catch (Exception ignored) { }
            throw e;
        }
    }

    public List<String> unanswered() {
        return unanswered;
    }

    public List<String> answeredLog() {
        return answeredLog;
    }

    public boolean isWindowOpen() {
        try {
            return !page.isClosed();
        } catch (Exception e) {
            return false;
        }
    }

    public String currentUrl() {
        try {
            return page.url();
        } catch (Exception e) {
            return "";
        }
    }

    /** Esegue la candidatura; cvPath può essere null (nessun CV generato). */
    public ApplyOutcome apply(JobSource source, String jobUrl, String cvPath, boolean autoSubmit) {
        return switch (source) {
            case LINKEDIN -> applyLinkedIn(jobUrl, cvPath, autoSubmit);
            case INDEED -> applyIndeed(jobUrl, cvPath, autoSubmit);
            case ALTRO -> {
                page.navigate(jobUrl);
                say("Annuncio esterno aperto: compila la candidatura nella finestra");
                yield ApplyOutcome.EXTERNAL;
            }
        };
    }

    // ---------------------------------------------------------------- LinkedIn

    private ApplyOutcome applyLinkedIn(String jobUrl, String cvPath, boolean autoSubmit) {
        page.navigate(jobUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        if (page.url().contains("/login") || page.url().contains("/authwall") || page.url().contains("/checkpoint")) {
            say("LinkedIn chiede l'accesso: usa \"Accedi\" nella sezione Account collegati della pagina Ricerca, poi riprova");
            if (sessions != null) sessions.markLoggedOut(BrowserSite.LINKEDIN, "LinkedIn ha chiesto di nuovo l'accesso");
            return ApplyOutcome.LOGIN_REQUIRED;
        }
        Locator applyButton = page.locator("button.jobs-apply-button, .jobs-apply-button--top-card button, button:has-text(\"Candidatura semplice\"), button:has-text(\"Easy Apply\")").first();
        try {
            applyButton.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (Exception e) {
            Locator external = page.locator("a:has-text(\"Candidati\"), button:has-text(\"Candidati\"), a:has-text(\"Apply\")").first();
            if (external.count() > 0) {
                say("Questo annuncio rimanda al sito dell'azienda: lo apro, completa tu la candidatura");
                try { external.click(); } catch (Exception ignored) { }
                return ApplyOutcome.EXTERNAL;
            }
            say("Pulsante di candidatura non trovato: forse ti sei già candidato o l'annuncio è chiuso");
            return ApplyOutcome.FAILED;
        }
        String label = safeText(applyButton);
        if (!label.toLowerCase().contains("semplice") && !label.toLowerCase().contains("easy")) {
            say("Candidatura sul sito dell'azienda (non \"semplice\"): apro la pagina, completa tu");
            try { applyButton.click(); } catch (Exception ignored) { }
            return ApplyOutcome.EXTERNAL;
        }
        applyButton.click();
        say("Modulo \"Candidatura semplice\" aperto");

        for (int step = 0; step < MAX_STEPS; step++) {
            page.waitForTimeout(1200);
            Locator modal = page.locator(".jobs-easy-apply-modal, div[role=dialog]").first();
            if (modal.count() == 0) {
                say("Il modulo si è chiuso");
                return isSubmittedLinkedIn() ? ApplyOutcome.SUBMITTED : ApplyOutcome.FAILED;
            }
            fillStep(modal, cvPath);

            Locator submit = modal.locator("button[aria-label*=\"Invia\" i], button[aria-label*=\"Submit\" i], button:has-text(\"Invia candidatura\"), button:has-text(\"Submit application\")").first();
            if (submit.count() > 0 && submit.isVisible()) {
                if (!unanswered.isEmpty()) {
                    say("Mancano risposte a " + unanswered.size() + " domande: completale nella finestra e invia tu");
                    return ApplyOutcome.NEEDS_INPUT;
                }
                if (!autoSubmit) {
                    say("Modulo compilato: controlla nella finestra e premi \"Invia candidatura\"");
                    return ApplyOutcome.READY_FOR_REVIEW;
                }
                uncheckFollowCompany(modal);
                submit.click();
                page.waitForTimeout(2500);
                say("Candidatura inviata");
                return ApplyOutcome.SUBMITTED;
            }

            Locator next = modal.locator("button[aria-label*=\"Continua\" i], button[aria-label*=\"Avanti\" i], button[aria-label*=\"Rivedi\" i], button[aria-label*=\"Next\" i], button[aria-label*=\"Review\" i], button:has-text(\"Avanti\"), button:has-text(\"Continua\"), button:has-text(\"Rivedi\"), button:has-text(\"Next\"), button:has-text(\"Review\")").first();
            if (next.count() == 0) {
                say("Non trovo il pulsante per andare avanti: continua tu nella finestra");
                return ApplyOutcome.NEEDS_INPUT;
            }
            next.click();
            page.waitForTimeout(800);
            // errori di validazione = campi obbligatori non compilati
            Locator errors = modal.locator(".artdeco-inline-feedback--error, [role=alert]");
            if (errors.count() > 0 && errors.first().isVisible()) {
                say("Il modulo segnala campi da completare: finisci tu nella finestra");
                return ApplyOutcome.NEEDS_INPUT;
            }
        }
        say("Troppi passaggi: continua tu nella finestra");
        return ApplyOutcome.NEEDS_INPUT;
    }

    private boolean isSubmittedLinkedIn() {
        try {
            return page.locator("text=/candidatura.*inviata|application.*sent/i").count() > 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void uncheckFollowCompany(Locator modal) {
        try {
            Locator follow = modal.locator("input[type=checkbox][id*=follow], label:has-text(\"Segui\") input, label:has-text(\"Follow\") input").first();
            if (follow.count() > 0 && follow.isChecked()) follow.uncheck();
        } catch (Exception ignored) {
            // facoltativo
        }
    }

    // ---------------------------------------------------------------- Indeed

    private ApplyOutcome applyIndeed(String jobUrl, String cvPath, boolean autoSubmit) {
        page.navigate(jobUrl);
        page.waitForLoadState(LoadState.DOMCONTENTLOADED);
        if (page.url().contains("secure.indeed.com/auth") || page.url().contains("/account/login")) {
            say("Indeed chiede l'accesso: usa \"Accedi\" nella sezione Account collegati della pagina Ricerca, poi riprova");
            if (sessions != null) sessions.markLoggedOut(BrowserSite.INDEED, "Indeed ha chiesto di nuovo l'accesso");
            return ApplyOutcome.LOGIN_REQUIRED;
        }
        Locator applyButton = page.locator("#indeedApplyButton, button[data-testid=indeedApplyButton], [data-testid=viewjob-indeed-apply] button, button:has-text(\"Candidati ora\"), button:has-text(\"Apply now\")").first();
        try {
            applyButton.waitFor(new Locator.WaitForOptions().setTimeout(15_000));
        } catch (Exception e) {
            Locator external = page.locator("a:has-text(\"Candidati sul sito\"), a:has-text(\"Apply on company site\"), [data-testid=primary-apply-action] a").first();
            if (external.count() > 0) {
                say("Questo annuncio rimanda al sito dell'azienda: lo apro, completa tu la candidatura");
                try { external.click(); } catch (Exception ignored) { }
                return ApplyOutcome.EXTERNAL;
            }
            say("Pulsante di candidatura non trovato: forse ti sei già candidato o l'annuncio è chiuso");
            return ApplyOutcome.FAILED;
        }
        String label = safeText(applyButton);
        if (label.toLowerCase().contains("sito") || label.toLowerCase().contains("company")) {
            say("Candidatura sul sito dell'azienda: apro la pagina, completa tu");
            try { applyButton.click(); } catch (Exception ignored) { }
            return ApplyOutcome.EXTERNAL;
        }
        applyButton.click();
        say("Modulo di candidatura Indeed aperto");

        for (int step = 0; step < MAX_STEPS; step++) {
            page.waitForTimeout(1500);
            // il modulo Indeed può essere nella pagina (smartapply) o in un iframe
            Frame form = findApplyFrame();
            Locator root = form == null ? page.locator("body") : form.locator("body");
            fillStep(root, cvPath);

            Locator submit = root.locator("button:has-text(\"Invia la candidatura\"), button:has-text(\"Invia candidatura\"), button:has-text(\"Submit your application\"), button:has-text(\"Submit application\")").first();
            if (submit.count() > 0 && submit.isVisible()) {
                if (!unanswered.isEmpty()) {
                    say("Mancano risposte a " + unanswered.size() + " domande: completale nella finestra e invia tu");
                    return ApplyOutcome.NEEDS_INPUT;
                }
                if (!autoSubmit) {
                    say("Modulo compilato: controlla nella finestra e premi \"Invia la candidatura\"");
                    return ApplyOutcome.READY_FOR_REVIEW;
                }
                submit.click();
                page.waitForTimeout(3000);
                say("Candidatura inviata");
                return ApplyOutcome.SUBMITTED;
            }
            Locator next = root.locator("button:has-text(\"Continua\"), button:has-text(\"Continue\"), button:has-text(\"Avanti\"), button:has-text(\"Rivedi\"), button:has-text(\"Review\"), button[data-testid*=continue i]").first();
            if (next.count() == 0 || !next.isVisible()) {
                say("Non trovo il pulsante per andare avanti: continua tu nella finestra");
                return ApplyOutcome.NEEDS_INPUT;
            }
            next.click();
        }
        say("Troppi passaggi: continua tu nella finestra");
        return ApplyOutcome.NEEDS_INPUT;
    }

    private Frame findApplyFrame() {
        for (Frame f : page.frames()) {
            String url = f.url() == null ? "" : f.url();
            if (url.contains("smartapply") || url.contains("apply.indeed") || url.contains("indeedapply")) return f;
        }
        return null;
    }

    // ---------------------------------------------------------------- compilazione generica

    /** Compila i campi visibili del passaggio corrente: CV, testo, tendine, scelte. */
    private void fillStep(Locator root, String cvPath) {
        // 1. caricamento CV
        if (cvPath != null) {
            try {
                Locator file = root.locator("input[type=file]").first();
                if (file.count() > 0) {
                    file.setInputFiles(Path.of(cvPath));
                    say("CV caricato: " + Path.of(cvPath).getFileName());
                    page.waitForTimeout(1500);
                }
            } catch (Exception e) {
                log.debug("Upload CV non riuscito: {}", e.getMessage());
            }
        }
        // 2. campi di testo e numerici
        Locator inputs = root.locator("input[type=text], input[type=tel], input[type=email], input[type=number], input:not([type]), textarea");
        int n = inputs.count();
        for (int i = 0; i < n; i++) {
            Locator input = inputs.nth(i);
            try {
                if (!input.isVisible() || !input.isEditable()) continue;
                String existing = input.inputValue();
                if (existing != null && !existing.isBlank()) continue;
                String question = labelOf(root, input);
                if (question.isBlank()) continue;
                String answer = resolve(question, null);
                if (answer == null) { markUnanswered(question); continue; }
                input.fill(answer);
                answeredLog.add(question + " → " + answer);
            } catch (Exception e) {
                log.debug("Campo non compilato: {}", e.getMessage());
            }
        }
        // 3. tendine
        Locator selects = root.locator("select");
        int s = selects.count();
        for (int i = 0; i < s; i++) {
            Locator select = selects.nth(i);
            try {
                if (!select.isVisible()) continue;
                String current = select.inputValue();
                List<String> options = select.locator("option").allInnerTexts();
                List<String> real = new ArrayList<>();
                for (String o : options) {
                    String t = o.strip();
                    if (!t.isBlank() && !t.toLowerCase().matches("(seleziona|select|scegli|choose).*")) real.add(t);
                }
                boolean placeholder = current == null || current.isBlank() || options.stream().anyMatch(o -> o.strip().toLowerCase().matches("(seleziona|select|scegli|choose).*"))
                        && select.locator("option:checked").count() > 0 && select.locator("option:checked").first().innerText().toLowerCase().matches("(seleziona|select|scegli|choose).*");
                if (!placeholder) continue;
                String question = labelOf(root, select);
                String answer = resolve(question, real);
                if (answer == null) { markUnanswered(question); continue; }
                select.selectOption(new com.microsoft.playwright.options.SelectOption().setLabel(answer));
                answeredLog.add(question + " → " + answer);
            } catch (Exception e) {
                log.debug("Tendina non compilata: {}", e.getMessage());
            }
        }
        // 4. scelte (radio): gruppi per name
        Locator radios = root.locator("input[type=radio]");
        int r = radios.count();
        List<String> doneGroups = new ArrayList<>();
        for (int i = 0; i < r; i++) {
            Locator radio = radios.nth(i);
            try {
                String name = radio.getAttribute("name");
                if (name == null || doneGroups.contains(name)) continue;
                doneGroups.add(name);
                Locator group = root.locator("input[type=radio][name=\"" + name + "\"]");
                boolean anyChecked = false;
                List<String> options = new ArrayList<>();
                int g = group.count();
                for (int k = 0; k < g; k++) {
                    if (group.nth(k).isChecked()) anyChecked = true;
                    options.add(optionLabel(root, group.nth(k)));
                }
                if (anyChecked) continue;
                String question = fieldsetQuestion(root, radio);
                String answer = resolve(question, options);
                if (answer == null) { markUnanswered(question.isBlank() ? String.join(" / ", options) : question); continue; }
                for (int k = 0; k < g; k++) {
                    if (options.get(k).equals(answer)) { group.nth(k).check(); break; }
                }
                answeredLog.add(question + " → " + answer);
            } catch (Exception e) {
                log.debug("Scelta non compilata: {}", e.getMessage());
            }
        }
    }

    private String resolve(String question, List<String> options) {
        String answer = matcher.answer(question, options);
        if (answer == null && aiAnswer != null) {
            try {
                answer = aiAnswer.apply(question, options);
                if (answer != null && options != null && !options.isEmpty()) answer = AnswerMatcher.pickOption(answer, options);
            } catch (Exception e) {
                log.debug("Risposta AI non disponibile: {}", e.getMessage());
            }
        }
        return answer == null || answer.isBlank() ? null : answer;
    }

    private void markUnanswered(String question) {
        if (!question.isBlank() && !unanswered.contains(question)) {
            unanswered.add(question);
            say("Domanda senza risposta: \"" + question + "\"");
        }
    }

    /** Testo dell'etichetta associata a un campo (label[for], aria-label, placeholder, label contenitore). */
    private String labelOf(Locator root, Locator field) {
        try {
            String id = field.getAttribute("id");
            if (id != null && !id.isBlank()) {
                Locator label = root.locator("label[for=\"" + id + "\"]");
                if (label.count() > 0) return clean(label.first().innerText());
            }
            String aria = field.getAttribute("aria-label");
            if (aria != null && !aria.isBlank()) return clean(aria);
            String labelledBy = field.getAttribute("aria-labelledby");
            if (labelledBy != null && !labelledBy.isBlank()) {
                Locator label = root.locator("#" + labelledBy.split("\\s+")[0]);
                if (label.count() > 0) return clean(label.first().innerText());
            }
            String placeholder = field.getAttribute("placeholder");
            if (placeholder != null && !placeholder.isBlank()) return clean(placeholder);
            ElementHandle handle = field.elementHandle();
            Object text = handle.evaluate("el => { const l = el.closest('label'); if (l) return l.innerText; const c = el.closest('div'); return c ? (c.querySelector('label, legend, span')?.innerText || '') : ''; }");
            return clean(String.valueOf(text));
        } catch (Exception e) {
            return "";
        }
    }

    private String optionLabel(Locator root, Locator radio) {
        try {
            String id = radio.getAttribute("id");
            if (id != null) {
                Locator label = root.locator("label[for=\"" + id + "\"]");
                if (label.count() > 0) return clean(label.first().innerText());
            }
            Object text = radio.elementHandle().evaluate("el => el.closest('label')?.innerText || el.value || ''");
            return clean(String.valueOf(text));
        } catch (Exception e) {
            return "";
        }
    }

    private String fieldsetQuestion(Locator root, Locator radio) {
        try {
            Object text = radio.elementHandle().evaluate("el => { const f = el.closest('fieldset'); if (f) { const l = f.querySelector('legend, span[aria-hidden], .fb-dash-form-element__label'); if (l) return l.innerText; } const c = el.closest('div[class*=form], div[class*=question], div'); return c?.querySelector('legend, label, span')?.innerText || ''; }");
            return clean(String.valueOf(text));
        } catch (Exception e) {
            return "";
        }
    }

    private static String clean(String s) {
        if (s == null) return "";
        return s.replaceAll("\\s+", " ").replaceAll("\\*", "").replaceAll("(?i)obbligatorio|required", "").strip();
    }

    private static String safeText(Locator l) {
        try {
            return l.innerText();
        } catch (Exception e) {
            return "";
        }
    }

    private void say(String message) {
        progress.accept(message);
    }

    @Override
    public void close() {
        try { context.close(); } catch (Exception ignored) { }
        try { playwright.close(); } catch (Exception ignored) { }
    }
}
