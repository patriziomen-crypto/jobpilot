package it.patrizio.jobpilot.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Lettera di presentazione generata per un'offerta. */
public record CoverLetterResult(
        @JsonPropertyDescription("Oggetto della lettera, es. 'Candidatura per Junior Java Developer'")
        String subject,
        @JsonPropertyDescription("Formula di apertura, es. 'Gentile team di selezione di Acme,'")
        String greeting,
        @JsonPropertyDescription("Paragrafi del corpo, 3 o 4, ciascuno di 2-4 frasi, senza formule di chiusura")
        List<String> paragraphs,
        @JsonPropertyDescription("Formula di chiusura, es. 'Cordiali saluti,'")
        String closing
) {
    /** Testo completo della lettera, pronto da copiare negli appunti. */
    public String toPlainText(String signature) {
        StringBuilder sb = new StringBuilder();
        if (subject != null && !subject.isBlank()) sb.append("Oggetto: ").append(subject).append("\n\n");
        if (greeting != null) sb.append(greeting).append("\n\n");
        if (paragraphs != null) {
            for (String p : paragraphs) sb.append(p.strip()).append("\n\n");
        }
        if (closing != null) sb.append(closing).append('\n');
        sb.append(signature);
        return sb.toString().strip();
    }
}
