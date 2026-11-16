package it.patrizio.jobpilot.ai;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** CV riadattato all'offerta: stessi fatti del profilo, riordinati e riformulati. */
public record TailoredCvResult(
        @JsonPropertyDescription("Titolo professionale adattato al ruolo (es. 'Sviluppatore Junior Java'), senza inventare seniority")
        String headline,
        @JsonPropertyDescription("Profilo professionale di 3-5 righe, riscritto per l'offerta usando le sue parole chiave")
        String summary,
        @JsonPropertyDescription("Esperienze, nell'ordine più rilevante per l'offerta")
        List<ExperienceOut> experiences,
        @JsonPropertyDescription("Formazione e certificazioni, nell'ordine più rilevante")
        List<EducationOut> educations,
        @JsonPropertyDescription("Competenze raggruppate per categoria, con le più pertinenti per prime")
        List<SkillGroupOut> skillGroups
) {
    public record ExperienceOut(String company, String role, String periodLabel,
                                @JsonPropertyDescription("Punti elenco riformulati, massimo 4") List<String> bullets) {}

    public record EducationOut(String institution, String title, String periodLabel,
                               @JsonPropertyDescription("Dettagli, massimo 4 punti") List<String> details) {}

    public record SkillGroupOut(String category, List<String> skills) {}
}
