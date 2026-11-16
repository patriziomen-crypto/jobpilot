package it.patrizio.jobpilot.search;

import it.patrizio.jobpilot.config.JobPilotException;

import java.util.Set;
import java.util.regex.Pattern;

/** I siti a cui l'utente può accedere nel browser dell'app, con i cookie che rivelano se l'accesso è attivo. */
public enum BrowserSite {
    LINKEDIN("LinkedIn", "https://www.linkedin.com/login", "https://www.linkedin.com/",
            Pattern.compile(".*linkedin\\.com$"), Set.of("li_at")),
    INDEED("Indeed", "https://it.indeed.com/account/login?hl=it&co=IT", "https://it.indeed.com/",
            Pattern.compile(".*indeed\\.com$"), Set.of("PPID"));

    private final String label;
    private final String loginUrl;
    private final String cookieUrl;
    private final Pattern cookieDomain;
    private final Set<String> authCookies;

    BrowserSite(String label, String loginUrl, String cookieUrl, Pattern cookieDomain, Set<String> authCookies) {
        this.label = label;
        this.loginUrl = loginUrl;
        this.cookieUrl = cookieUrl;
        this.cookieDomain = cookieDomain;
        this.authCookies = authCookies;
    }

    public String label() { return label; }
    public String loginUrl() { return loginUrl; }
    public String cookieUrl() { return cookieUrl; }
    public Pattern cookieDomain() { return cookieDomain; }
    public Set<String> authCookies() { return authCookies; }

    /** Da "linkedin" / "indeed" (URL) al sito; 400 se sconosciuto. */
    public static BrowserSite parse(String name) {
        for (BrowserSite s : values()) {
            if (s.name().equalsIgnoreCase(name)) return s;
        }
        throw JobPilotException.badRequest("Sito sconosciuto: " + name + " (usa linkedin o indeed)");
    }
}
