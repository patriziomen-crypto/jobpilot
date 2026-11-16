package it.patrizio.jobpilot.search;

import it.patrizio.jobpilot.config.JobPilotException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Il profilo del browser dedicato (Edge) può essere usato da un solo processo alla volta:
 * ricerca Indeed, finestra di login, invio assistito. Questo lucchetto lo garantisce.
 */
@Component
public class BrowserLock {

    private final AtomicBoolean busy = new AtomicBoolean(false);
    private final AtomicReference<String> owner = new AtomicReference<>("");

    /** Prende il lucchetto o lancia 409 con il nome di chi lo sta usando. */
    public void acquire(String who) {
        if (!busy.compareAndSet(false, true)) {
            throw new JobPilotException(HttpStatus.CONFLICT,
                    "Il browser dell'app è già in uso (" + owner.get() + "): aspetta che finisca o chiudi quella finestra");
        }
        owner.set(who);
    }

    public void release() {
        owner.set("");
        busy.set(false);
    }

    public boolean isBusy() {
        return busy.get();
    }

    public String owner() {
        return owner.get();
    }
}
