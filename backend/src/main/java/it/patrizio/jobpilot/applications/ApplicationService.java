package it.patrizio.jobpilot.applications;

import it.patrizio.jobpilot.ai.AiRouter;
import it.patrizio.jobpilot.ai.JobAnalysisResult;
import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.jobs.JobAnalysis;
import it.patrizio.jobpilot.jobs.JobAnalysisRepository;
import it.patrizio.jobpilot.jobs.JobImportRequest;
import it.patrizio.jobpilot.jobs.JobImportService;
import it.patrizio.jobpilot.jobs.JobOffer;
import it.patrizio.jobpilot.jobs.JobOfferRepository;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.ProfileContext;
import it.patrizio.jobpilot.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Ciclo di vita di una candidatura della persona attiva: import, analisi, cambio di stato. */
@Service
public class ApplicationService {

    private static final int GIORNI_FOLLOW_UP = 7;

    private final ApplicationRepository applications;
    private final JobAnalysisRepository analyses;
    private final JobOfferRepository offers;
    private final JobImportService importService;
    private final ProfileService profileService;
    private final ProfileContext profileContext;
    private final AiRouter ai;
    private final TransactionTemplate tx;

    public ApplicationService(ApplicationRepository applications, JobAnalysisRepository analyses, JobOfferRepository offers,
                              JobImportService importService, ProfileService profileService, ProfileContext profileContext,
                              AiRouter ai, PlatformTransactionManager transactionManager) {
        this.applications = applications;
        this.analyses = analyses;
        this.offers = offers;
        this.importService = importService;
        this.profileService = profileService;
        this.profileContext = profileContext;
        this.ai = ai;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Importa l'offerta e crea la candidatura in stato TROVATA per la persona attiva. */
    @Transactional
    public ApplicationDto importAndCreate(JobImportRequest request) {
        return importAndCreate(request, profileContext.currentProfileId());
    }

    /** Come sopra, per una persona precisa (usato dalla ricerca automatica che gira fuori dalle richieste web). */
    @Transactional
    public ApplicationDto importAndCreate(JobImportRequest request, Long profileId) {
        JobImportService.ImportResult result = importService.importJob(request);
        JobOffer offer = result.offer();
        Application app = applications.findByJobOfferIdAndProfileId(offer.getId(), profileId).orElseGet(() -> {
            Application a = new Application();
            a.setProfile(profileService.getProfile(profileId));
            a.setJobOffer(offer);
            a.setStatus(ApplicationStatus.TROVATA);
            return applications.save(a);
        });
        return toDto(app);
    }

    /** True se la persona ha già una candidatura per quell'offerta (URL normalizzato). */
    @Transactional(readOnly = true)
    public boolean alreadyHas(String url, Long profileId) {
        String hash = JobImportService.sha256(JobImportService.normalizeUrl(url));
        return offers.findByUrlHash(hash)
                .map(o -> applications.findByJobOfferIdAndProfileId(o.getId(), profileId).isPresent())
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<ApplicationDto> findAll() {
        Long profileId = profileContext.currentProfileId();
        List<Application> list = applications.findAllByProfileIdOrderByUpdatedAtDesc(profileId);
        // Tutte le analisi della persona in una sola query, invece di una per candidatura
        Map<Long, JobAnalysis> byApplication = new HashMap<>();
        for (JobAnalysis a : analyses.findAllByApplicationProfileId(profileId)) {
            byApplication.put(a.getApplication().getId(), a);
        }
        return list.stream().map(app -> ApplicationDto.from(app, byApplication.get(app.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public ApplicationDto get(Long id) {
        return toDto(find(id));
    }

    /** Analizza l'offerta della candidatura della persona attiva. */
    public ApplicationDto analyze(Long id) {
        return analyze(id, profileContext.currentProfileId());
    }

    /**
     * Analizza l'offerta per la persona indicata. La chiamata all'AI (anche lunga) avviene FUORI dalla
     * transazione, così non tiene occupata una connessione al database nel frattempo.
     */
    public ApplicationDto analyze(Long id, Long profileId) {
        // 1. lettura: profilo e offerta (in transazione)
        record Input(Profile profile, String profileText, JobOffer offer) {}
        Input input = tx.execute(status -> {
            Application app = applications.findByIdAndProfileId(id, profileId)
                    .orElseThrow(() -> JobPilotException.notFound("Candidatura " + id + " non trovata"));
            Profile profile = profileService.getProfile(profileId);
            return new Input(profile, profileService.toPromptText(profile), app.getJobOffer());
        });

        // 2. analisi (fuori transazione)
        AiRouter.AiResult<JobAnalysisResult> result = ai.analyze(input.profile(), input.profileText(), input.offer());
        JobAnalysisResult r = result.value();

        // 3. salvataggio (in transazione)
        return tx.execute(status -> {
            Application app = applications.findByIdAndProfileId(id, profileId)
                    .orElseThrow(() -> JobPilotException.notFound("Candidatura " + id + " non trovata"));
            JobAnalysis analysis = analyses.findByApplicationId(app.getId()).orElseGet(JobAnalysis::new);
            analysis.setApplication(app);
            analysis.setScore(Math.max(0, Math.min(100, r.score())));
            analysis.setSummary(r.summary());
            analysis.setMatchedRequirements(r.matchedRequirements());
            analysis.setMissingRequirements(r.missingRequirements());
            analysis.setKeywords(r.keywords());
            analysis.setRedFlags(r.redFlags());
            analysis.setLanguage(r.language());
            analysis.setModel(result.model());
            analysis.setProvider(result.provider());
            analyses.save(analysis);

            JobOffer offer = app.getJobOffer();
            if (r.language() != null && offer.getLanguage() == null) {
                offer.setLanguage(r.language());
            }
            if (app.getStatus() == ApplicationStatus.TROVATA) {
                app.setStatus(ApplicationStatus.DA_VALUTARE);
            }
            return ApplicationDto.from(app, analysis);
        });
    }

    @Transactional
    public ApplicationDto changeStatus(Long id, ApplicationStatus status) {
        Application app = find(id);
        app.setStatus(status);
        if (status == ApplicationStatus.INVIATA && app.getSentAt() == null) {
            app.setSentAt(LocalDateTime.now());
            app.setFollowUpAt(LocalDate.now().plusDays(GIORNI_FOLLOW_UP));
        }
        return toDto(app);
    }

    @Transactional
    public ApplicationDto updateNotes(Long id, String notes, LocalDate followUpAt) {
        Application app = find(id);
        app.setNotes(notes);
        app.setFollowUpAt(followUpAt);
        return toDto(app);
    }

    /** Elimina la candidatura; l'offerta viene rimossa solo se nessun'altra persona la usa. */
    @Transactional
    public void delete(Long id) {
        Application app = find(id);
        JobOffer offer = app.getJobOffer();
        applications.delete(app);
        applications.flush();
        if (applications.countByJobOfferId(offer.getId()) == 0) {
            offers.delete(offer);
        }
    }

    /** Candidatura della persona attiva, o 404. Usato anche da documenti e "apri cartella". */
    @Transactional(readOnly = true)
    public Application findForCurrentProfile(Long id) {
        return find(id);
    }

    private Application find(Long id) {
        return applications.findByIdAndProfileId(id, profileContext.currentProfileId())
                .orElseThrow(() -> JobPilotException.notFound("Candidatura " + id + " non trovata"));
    }

    private ApplicationDto toDto(Application app) {
        JobAnalysis analysis = analyses.findByApplicationId(app.getId()).orElse(null);
        return ApplicationDto.from(app, analysis);
    }
}
