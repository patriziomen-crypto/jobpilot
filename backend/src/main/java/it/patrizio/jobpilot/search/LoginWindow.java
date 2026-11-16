package it.patrizio.jobpilot.search;

import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Playwright;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.List;

/** Avvio del browser dedicato (Edge, profilo persistente) usato da ricerca Indeed, finestra di accesso e invio assistito. */
public final class LoginWindow {

    private static final Logger log = LoggerFactory.getLogger(LoginWindow.class);

    private LoginWindow() {}

    /** Avvia Edge (o Chrome) con il profilo dedicato; headless = invisibile (verifiche e disconnessione). */
    public static BrowserContext launch(Playwright playwright, Path profileDir, boolean headless) {
        BrowserType.LaunchPersistentContextOptions options = new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(headless)
                .setLocale("it-IT")
                .setViewportSize(1280, 900)
                .setArgs(List.of("--disable-blink-features=AutomationControlled"));
        try {
            return playwright.chromium().launchPersistentContext(profileDir, options.setChannel("msedge"));
        } catch (Exception e) {
            log.info("Edge non disponibile ({}), provo Chrome", e.getMessage());
            return playwright.chromium().launchPersistentContext(profileDir, options.setChannel("chrome"));
        }
    }
}
