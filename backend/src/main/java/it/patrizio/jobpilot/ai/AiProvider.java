package it.patrizio.jobpilot.ai;

import it.patrizio.jobpilot.jobs.JobOffer;

/** Le tre operazioni che JobPilot chiede all'intelligenza artificiale. */
public interface AiProvider {

    /** Nome mostrato all'utente (es. "Claude", "GitHub Models", "Modalità base"). */
    String name();

    String model();

    JobAnalysisResult analyze(String profileText, JobOffer offer);

    TailoredCvResult tailorCv(String profileText, JobOffer offer, String keywords);

    CoverLetterResult coverLetter(String profileText, JobOffer offer, String analysisSummary);

    /** Chiamata minima di verifica; restituisce un messaggio per l'utente o lancia AiException. */
    String ping();

    /**
     * Risposta breve a una domanda di un modulo di candidatura, basata solo sul profilo.
     * Se ci sono opzioni, la risposta deve essere una di quelle. Null se non supportato.
     */
    default String shortAnswer(String profileText, String question, java.util.List<String> options) {
        return null;
    }

    /**
     * Conversazione con l'assistente dell'app: istruzioni di sistema (contesto) + storia dei messaggi.
     * Restituisce testo per l'utente ed eventuali azioni. I fornitori senza chat lanciano AiException.
     */
    default ChatReply chat(String system, java.util.List<ChatMessage> history) {
        throw new AiException(AiException.Kind.UNAVAILABLE, name() + " non supporta la chat");
    }

    /** Modelli che il fornitore mette a disposizione con questa chiave (lista vuota se non supportato). */
    default java.util.List<String> listModels() {
        return java.util.List.of();
    }
}
