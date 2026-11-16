package it.patrizio.jobpilot.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Risposta strutturata dell'assistente: testo per l'utente più eventuali azioni da eseguire nell'app. */
public record ChatReply(
        @JsonPropertyDescription("Risposta per l'utente, in italiano, testo semplice (niente tabelle, al massimo elenchi con '-')")
        String reply,
        @JsonPropertyDescription("Azioni da eseguire nell'app, SOLO se l'utente le ha chieste chiaramente; altrimenti lista vuota")
        List<Action> actions
) {
    public record Action(
            @JsonPropertyDescription("Uno tra: CHANGE_STATUS, ANALYZE, GENERATE, START_SEARCH, LOGIN, OPEN")
            String type,
            @JsonPropertyDescription("Numero della candidatura (per CHANGE_STATUS, ANALYZE, GENERATE), altrimenti stringa vuota")
            String applicationId,
            @JsonPropertyDescription("Per CHANGE_STATUS, uno tra: TROVATA, DA_VALUTARE, PRONTA, INVIATA, RISPOSTA, COLLOQUIO, OFFERTA, RIFIUTATA, SCADUTA; altrimenti stringa vuota")
            String status,
            @JsonPropertyDescription("Per LOGIN: linkedin oppure indeed; altrimenti stringa vuota")
            String site,
            @JsonPropertyDescription("Per OPEN, la pagina da aprire: /, /ricerca, /importa, /statistiche, /profilo, /impostazioni oppure /candidature/<numero>; altrimenti stringa vuota")
            String route
    ) {}

    public List<Action> safeActions() {
        return actions == null ? List.of() : actions;
    }
}
