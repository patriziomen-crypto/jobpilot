package it.patrizio.jobpilot.apply;

/** Come è finito un tentativo di invio assistito. */
public enum ApplyOutcome {
    /** Candidatura inviata dall'app (invio automatico attivo). */
    SUBMITTED,
    /** Modulo compilato: la finestra resta aperta, manca solo il click finale dell'utente. */
    READY_FOR_REVIEW,
    /** L'app non ha saputo rispondere a una domanda obbligatoria: la finestra resta aperta per l'utente. */
    NEEDS_INPUT,
    /** L'annuncio rimanda al sito dell'azienda: la finestra resta aperta sulla pagina esterna. */
    EXTERNAL,
    /** Serve prima l'accesso al sito nella finestra dell'app. */
    LOGIN_REQUIRED,
    /** Errore tecnico. */
    FAILED
}
