package it.patrizio.jobpilot.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Schema della risposta strutturata che chiediamo a Claude per l'analisi di un'offerta. */
public record JobAnalysisResult(
        @JsonPropertyDescription("Compatibilità tra candidato e offerta, da 0 (nessuna) a 100 (perfetta)")
        int score,
        @JsonPropertyDescription("2-3 frasi in italiano: perché conviene o non conviene candidarsi")
        String summary,
        @JsonPropertyDescription("Requisiti dell'annuncio che il candidato soddisfa, uno per voce")
        List<String> matchedRequirements,
        @JsonPropertyDescription("Requisiti dell'annuncio che il candidato NON soddisfa o soddisfa solo in parte")
        List<String> missingRequirements,
        @JsonPropertyDescription("Parole chiave dell'annuncio da usare nel CV e nella lettera (tecnologie, metodologie, soft skill)")
        List<String> keywords,
        @JsonPropertyDescription("Segnali di attenzione: es. esperienza senior richiesta, partita IVA, sede lontana, annuncio vago")
        List<String> redFlags,
        @JsonPropertyDescription("Lingua dell'annuncio come codice ISO: it, en, ...")
        String language
) {}
