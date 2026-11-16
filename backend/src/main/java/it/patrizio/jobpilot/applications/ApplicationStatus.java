package it.patrizio.jobpilot.applications;

/** Stati di una candidatura, nell'ordine in cui compaiono nella dashboard Kanban. */
public enum ApplicationStatus {
    TROVATA,       // offerta importata, non ancora analizzata
    DA_VALUTARE,   // analisi fatta, decidere se candidarsi
    PRONTA,        // CV e lettera generati, pronta per l'invio
    INVIATA,       // candidatura inviata sul sito
    RISPOSTA,      // l'azienda ha risposto
    COLLOQUIO,     // colloquio fissato o fatto
    OFFERTA,       // ricevuta un'offerta
    RIFIUTATA,     // rifiuto ricevuto
    SCADUTA        // annuncio chiuso o non più interessante
}
