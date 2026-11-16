package it.patrizio.jobpilot.profile;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/** Rappresentazione completa del profilo scambiata con il frontend. */
public record ProfileDto(
        Long id,
        @NotBlank(message = "il nome è obbligatorio") String fullName,
        @NotBlank(message = "il titolo professionale è obbligatorio") String headline,
        String location,
        String phone,
        String email,
        String linkedinUrl,
        String githubUrl,
        String summary,
        String languages,
        String extraInfo,
        List<ExperienceDto> experiences,
        List<EducationDto> educations,
        List<SkillDto> skills,
        List<StandardAnswerDto> standardAnswers
) {
    public record ExperienceDto(Long id, String company, String role, String periodLabel, String bullets) {}

    public record EducationDto(Long id, String institution, String title, String periodLabel, String details) {}

    public record SkillDto(Long id, String category, String name) {}

    public record StandardAnswerDto(Long id, String question, String answer) {}

    public static ProfileDto from(Profile p) {
        return new ProfileDto(
                p.getId(), p.getFullName(), p.getHeadline(), p.getLocation(), p.getPhone(), p.getEmail(),
                p.getLinkedinUrl(), p.getGithubUrl(), p.getSummary(), p.getLanguages(), p.getExtraInfo(),
                p.getExperiences().stream()
                        .map(e -> new ExperienceDto(e.getId(), e.getCompany(), e.getRole(), e.getPeriodLabel(), e.getBullets()))
                        .toList(),
                p.getEducations().stream()
                        .map(e -> new EducationDto(e.getId(), e.getInstitution(), e.getTitle(), e.getPeriodLabel(), e.getDetails()))
                        .toList(),
                p.getSkills().stream()
                        .map(s -> new SkillDto(s.getId(), s.getCategory(), s.getName()))
                        .toList(),
                p.getStandardAnswers().stream()
                        .map(a -> new StandardAnswerDto(a.getId(), a.getQuestion(), a.getAnswer()))
                        .toList()
        );
    }
}
