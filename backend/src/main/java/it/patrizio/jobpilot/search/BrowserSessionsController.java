package it.patrizio.jobpilot.search;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Accessi a LinkedIn e Indeed nel browser dell'app: stato, finestra di accesso, disconnessione, verifica. */
@RestController
@RequestMapping("/api/browser")
public class BrowserSessionsController {

    private final BrowserSessions sessions;

    public BrowserSessionsController(BrowserSessions sessions) {
        this.sessions = sessions;
    }

    @GetMapping("/status")
    public BrowserSessions.Status status() {
        return sessions.status();
    }

    @PostMapping("/login/{site}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BrowserSessions.Status login(@PathVariable String site) {
        sessions.openLogin(BrowserSite.parse(site));
        return sessions.status();
    }

    @PostMapping("/logout/{site}")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BrowserSessions.Status logout(@PathVariable String site) {
        sessions.logout(BrowserSite.parse(site));
        return sessions.status();
    }

    @PostMapping("/check")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public BrowserSessions.Status check() {
        sessions.check();
        return sessions.status();
    }
}
