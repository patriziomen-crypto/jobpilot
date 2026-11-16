package it.patrizio.jobpilot.config;

import org.springframework.http.HttpStatus;

/** Errore applicativo con messaggio pensato per l'utente e codice HTTP associato. */
public class JobPilotException extends RuntimeException {

    private final HttpStatus status;

    public JobPilotException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public JobPilotException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public static JobPilotException notFound(String message) {
        return new JobPilotException(HttpStatus.NOT_FOUND, message);
    }

    public static JobPilotException badRequest(String message) {
        return new JobPilotException(HttpStatus.BAD_REQUEST, message);
    }
}
