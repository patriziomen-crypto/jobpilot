package it.patrizio.jobpilot.jobs;

/**
 * Dati per importare un'offerta. Basta l'URL (il backend prova a scaricare la pagina) oppure il testo
 * incollato; gli altri campi sono facoltativi e, se presenti, hanno la precedenza su quanto estratto.
 */
public record JobImportRequest(
        String url,
        String text,
        String title,
        String company,
        String location,
        Boolean remote
) {
    public boolean hasUrl() {
        return url != null && !url.isBlank();
    }

    public boolean hasText() {
        return text != null && !text.isBlank();
    }
}
