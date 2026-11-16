package it.patrizio.jobpilot.applications;

import it.patrizio.jobpilot.jobs.JobAnalysis;
import it.patrizio.jobpilot.jobs.JobOffer;
import it.patrizio.jobpilot.jobs.JobSource;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Candidatura + offerta + eventuale analisi, come la vede il frontend. */
public record ApplicationDto(
        Long id,
        Long profileId,
        ApplicationStatus status,
        JobDto job,
        AnalysisDto analysis,
        String cvPath,
        String coverLetter,
        String letterPath,
        LocalDateTime sentAt,
        LocalDate followUpAt,
        String notes,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public record JobDto(
            Long id, JobSource source, String url, String title, String company, String location,
            boolean remote, String description, String language, LocalDateTime foundAt) {

        public static JobDto from(JobOffer j) {
            return new JobDto(j.getId(), j.getSource(), j.getUrl(), j.getTitle(), j.getCompany(), j.getLocation(),
                    j.isRemote(), j.getDescription(), j.getLanguage(), j.getFoundAt());
        }
    }

    public record AnalysisDto(
            int score, String summary, List<String> matchedRequirements, List<String> missingRequirements,
            List<String> keywords, List<String> redFlags, String language, String model, String provider,
            LocalDateTime createdAt) {

        public static AnalysisDto from(JobAnalysis a) {
            return new AnalysisDto(a.getScore(), a.getSummary(), a.getMatchedRequirements(), a.getMissingRequirements(),
                    a.getKeywords(), a.getRedFlags(), a.getLanguage(), a.getModel(), a.getProvider(), a.getCreatedAt());
        }
    }

    public static ApplicationDto from(Application app, JobAnalysis analysis) {
        return new ApplicationDto(
                app.getId(), app.getProfile().getId(), app.getStatus(), JobDto.from(app.getJobOffer()),
                analysis == null ? null : AnalysisDto.from(analysis),
                app.getCvPath(), app.getCoverLetter(), app.getLetterPath(), app.getSentAt(), app.getFollowUpAt(),
                app.getNotes(), app.getCreatedAt(), app.getUpdatedAt());
    }
}
