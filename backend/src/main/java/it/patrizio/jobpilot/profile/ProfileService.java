package it.patrizio.jobpilot.profile;

import it.patrizio.jobpilot.config.JobPilotException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Lettura, aggiornamento e gestione dei profili (una riga per persona). */
@Service
public class ProfileService {

    private final ProfileRepository repository;
    private final ProfileSearchRepository searches;
    private final ProfileContext context;

    public ProfileService(ProfileRepository repository, ProfileSearchRepository searches, ProfileContext context) {
        this.repository = repository;
        this.searches = searches;
        this.context = context;
    }

    /** Riassunto per il selettore delle persone. */
    public record ProfileSummary(Long id, String fullName, String headline) {
        static ProfileSummary from(Profile p) {
            return new ProfileSummary(p.getId(), p.getFullName(), p.getHeadline());
        }
    }

    public record NewProfileRequest(String fullName, String headline, Long copyFromId) {}

    @Transactional(readOnly = true)
    public Profile getProfile() {
        return getProfile(context.currentProfileId());
    }

    @Transactional(readOnly = true)
    public Profile getProfile(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> JobPilotException.notFound("Profilo " + id + " non trovato"));
    }

    @Transactional(readOnly = true)
    public ProfileDto getProfileDto() {
        return ProfileDto.from(getProfile());
    }

    @Transactional(readOnly = true)
    public List<ProfileSummary> list() {
        return repository.findAllByOrderByIdAsc().stream().map(ProfileSummary::from).toList();
    }

    /** Crea una nuova persona, vuota oppure copiando competenze e risposte pronte da un'altra. */
    @Transactional
    public ProfileSummary create(NewProfileRequest request) {
        if (request.fullName() == null || request.fullName().isBlank()) {
            throw JobPilotException.badRequest("Il nome della persona è obbligatorio");
        }
        Profile p = new Profile();
        p.setFullName(request.fullName().strip());
        p.setHeadline(request.headline() == null || request.headline().isBlank() ? "Candidato/a" : request.headline().strip());

        ProfileSearch search = new ProfileSearch();
        search.setProfile(p);

        if (request.copyFromId() != null) {
            Profile source = getProfile(request.copyFromId());
            p.setLocation(source.getLocation());
            p.setLanguages(source.getLanguages());
            for (Skill s : source.getSkills()) {
                Skill copy = new Skill();
                copy.setProfile(p);
                copy.setCategory(s.getCategory());
                copy.setName(s.getName());
                copy.setSortOrder(s.getSortOrder());
                p.getSkills().add(copy);
            }
            for (StandardAnswer a : source.getStandardAnswers()) {
                StandardAnswer copy = new StandardAnswer();
                copy.setProfile(p);
                copy.setQuestion(a.getQuestion());
                copy.setAnswer(a.getAnswer());
                copy.setSortOrder(a.getSortOrder());
                p.getStandardAnswers().add(copy);
            }
            searches.findByProfileId(source.getId()).ifPresent(src -> {
                search.setKeywords(src.getKeywords());
                search.setLocation(src.getLocation());
                search.setRemote(src.isRemote());
                search.setDays(src.getDays());
                search.setLinkedin(src.isLinkedin());
                search.setIndeed(src.isIndeed());
                search.setAutoAnalyze(src.isAutoAnalyze());
                search.setMaxPerQuery(src.getMaxPerQuery());
            });
        }
        Profile saved = repository.save(p);
        searches.save(search);
        return ProfileSummary.from(saved);
    }

    /** Elimina una persona con tutte le sue candidature; l'ultima rimasta non si può eliminare. */
    @Transactional
    public void delete(Long id) {
        if (repository.count() <= 1) {
            throw new JobPilotException(HttpStatus.CONFLICT, "Deve restare almeno una persona");
        }
        Profile p = getProfile(id);
        repository.delete(p);
    }

    /** Sostituisce per intero il profilo attivo e le sue liste con quanto arriva dal frontend. */
    @Transactional
    public ProfileDto update(ProfileDto dto) {
        Profile p = getProfile();
        p.setFullName(dto.fullName());
        p.setHeadline(dto.headline());
        p.setLocation(dto.location());
        p.setPhone(dto.phone());
        p.setEmail(dto.email());
        p.setLinkedinUrl(dto.linkedinUrl());
        p.setGithubUrl(dto.githubUrl());
        p.setSummary(dto.summary());
        p.setLanguages(dto.languages());
        p.setExtraInfo(dto.extraInfo());

        p.getExperiences().clear();
        List<ProfileDto.ExperienceDto> experiences = dto.experiences() == null ? List.of() : dto.experiences();
        for (int i = 0; i < experiences.size(); i++) {
            ProfileDto.ExperienceDto e = experiences.get(i);
            Experience entity = new Experience();
            entity.setProfile(p);
            entity.setCompany(e.company());
            entity.setRole(e.role());
            entity.setPeriodLabel(e.periodLabel());
            entity.setBullets(e.bullets());
            entity.setSortOrder(i);
            p.getExperiences().add(entity);
        }

        p.getEducations().clear();
        List<ProfileDto.EducationDto> educations = dto.educations() == null ? List.of() : dto.educations();
        for (int i = 0; i < educations.size(); i++) {
            ProfileDto.EducationDto e = educations.get(i);
            Education entity = new Education();
            entity.setProfile(p);
            entity.setInstitution(e.institution());
            entity.setTitle(e.title());
            entity.setPeriodLabel(e.periodLabel());
            entity.setDetails(e.details());
            entity.setSortOrder(i);
            p.getEducations().add(entity);
        }

        p.getSkills().clear();
        List<ProfileDto.SkillDto> skills = dto.skills() == null ? List.of() : dto.skills();
        for (int i = 0; i < skills.size(); i++) {
            ProfileDto.SkillDto s = skills.get(i);
            Skill entity = new Skill();
            entity.setProfile(p);
            entity.setCategory(s.category());
            entity.setName(s.name());
            entity.setSortOrder(i);
            p.getSkills().add(entity);
        }

        p.getStandardAnswers().clear();
        List<ProfileDto.StandardAnswerDto> answers = dto.standardAnswers() == null ? List.of() : dto.standardAnswers();
        for (int i = 0; i < answers.size(); i++) {
            ProfileDto.StandardAnswerDto a = answers.get(i);
            StandardAnswer entity = new StandardAnswer();
            entity.setProfile(p);
            entity.setQuestion(a.question());
            entity.setAnswer(a.answer());
            entity.setSortOrder(i);
            p.getStandardAnswers().add(entity);
        }

        return ProfileDto.from(repository.save(p));
    }

    /** Testo del profilo da dare all'AI come fonte di verità. */
    public String toPromptText(Profile p) {
        StringBuilder sb = new StringBuilder();
        sb.append("Nome: ").append(p.getFullName()).append('\n');
        sb.append("Titolo: ").append(p.getHeadline()).append('\n');
        if (p.getLocation() != null) sb.append("Zona: ").append(p.getLocation()).append('\n');
        if (p.getSummary() != null) sb.append("\nProfilo professionale:\n").append(p.getSummary()).append('\n');

        sb.append("\nEsperienze:\n");
        for (Experience e : p.getExperiences()) {
            sb.append("- ").append(e.getRole()).append(" presso ").append(e.getCompany());
            if (e.getPeriodLabel() != null) sb.append(" (").append(e.getPeriodLabel()).append(")");
            sb.append('\n');
            if (e.getBullets() != null) {
                for (String b : e.getBullets().split("\\R")) {
                    if (!b.isBlank()) sb.append("    • ").append(b.trim()).append('\n');
                }
            }
        }

        sb.append("\nFormazione:\n");
        for (Education e : p.getEducations()) {
            sb.append("- ").append(e.getTitle()).append(" — ").append(e.getInstitution());
            if (e.getPeriodLabel() != null) sb.append(" (").append(e.getPeriodLabel()).append(")");
            sb.append('\n');
            if (e.getDetails() != null) {
                for (String d : e.getDetails().split("\\R")) {
                    if (!d.isBlank()) sb.append("    • ").append(d.trim()).append('\n');
                }
            }
        }

        sb.append("\nCompetenze:\n");
        String lastCategory = null;
        List<Skill> skills = p.getSkills();
        for (int i = 0; i < skills.size(); i++) {
            Skill s = skills.get(i);
            if (!s.getCategory().equals(lastCategory)) {
                sb.append("- ").append(s.getCategory()).append(": ");
                lastCategory = s.getCategory();
            } else {
                sb.append(", ");
            }
            sb.append(s.getName());
            boolean last = i == skills.size() - 1;
            if (last || !skills.get(i + 1).getCategory().equals(lastCategory)) {
                sb.append('\n');
            }
        }

        if (p.getLanguages() != null) sb.append("\nLingue: ").append(p.getLanguages()).append('\n');
        if (p.getExtraInfo() != null) sb.append("Altro: ").append(p.getExtraInfo()).append('\n');
        return sb.toString();
    }
}
