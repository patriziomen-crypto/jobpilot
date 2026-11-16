package it.patrizio.jobpilot.ai;

/** Errore di un fornitore AI, classificato per decidere se passare al fornitore successivo. */
public class AiException extends RuntimeException {

    public enum Kind {
        AUTH,          // chiave mancante o non valida
        QUOTA,         // credito o abbonamento esaurito
        RATE_LIMIT,    // troppe richieste
        UNAVAILABLE,   // rete o servizio non raggiungibile
        REFUSED,       // il modello ha rifiutato la richiesta
        BAD_RESPONSE   // risposta non interpretabile
    }

    private final Kind kind;

    public AiException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AiException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
