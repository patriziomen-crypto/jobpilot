package it.patrizio.jobpilot.documents;

import it.patrizio.jobpilot.ai.AiRouter;
import it.patrizio.jobpilot.ai.CoverLetterResult;
import it.patrizio.jobpilot.ai.TailoredCvResult;
import it.patrizio.jobpilot.applications.Application;
import it.patrizio.jobpilot.applications.ApplicationRepository;
import it.patrizio.jobpilot.applications.ApplicationStatus;
import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.config.JobPilotProperties;
import it.patrizio.jobpilot.jobs.JobAnalysis;
import it.patrizio.jobpilot.jobs.JobAnalysisRepository;
import it.patrizio.jobpilot.jobs.JobOffer;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.ProfileContext;
import it.patrizio.jobpilot.profile.ProfileService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Path;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Genera CV su misura e lettera di presentazione (testo + PDF) per una candidatura della persona attiva. */
@Service
public class DocumentService {

    private final ApplicationRepository applications;
    private final JobAnalysisRepository analyses;
    private final ProfileService profileService;
    private final ProfileContext profileContext;
    private final AiRouter ai;
    private final PdfService pdf;
    private final JobPilotProperties properties;
    private final TransactionTemplate tx;

    public DocumentService(ApplicationRepository applications, JobAnalysisRepository analyses,
                           ProfileService profileService, ProfileContext profileContext, AiRouter ai, PdfService pdf,
                           JobPilotProperties properties, PlatformTransactionManager transactionManager) {
        this.applications = applications;
        this.analyses = analyses;
        this.profileService = profileService;
        this.profileContext = profileContext;
        this.ai = ai;
        this.pdf = pdf;
        this.properties = properties;
        this.tx = new TransactionTemplate(transactionManager);
    }

    /** Esito della generazione: percorsi dei PDF, testo della lettera, chi li ha scritti. */
    public record Generated(String cvPath, String letterPath, String coverLetter, List<String> providers) {}

    public Generated generate(Long applicationId, boolean cv, boolean letter) {
        Long profileId = profileContext.currentProfileId();

        // 1. lettura (in transazione): profilo completo, offerta, analisi
        record Input(Profile profile, String profileText, JobOffer offer, JobAnalysis analysis) {}
        Input in = tx.execute(status -> {
            Application app = applications.findByIdAndProfileId(applicationId, profileId)
                    .orElseThrow(() -> JobPilotException.notFound("Candidatura " + applicationId + " non trovata"));
            Profile profile = profileService.getProfile(profileId);
            // tocca le liste del profilo per averle disponibili fuori transazione (template PDF)
            profile.getExperiences().size();
            profile.getEducations().size();
            profile.getSkills().size();
            profile.getStandardAnswers().size();
            return new Input(profile, profileService.toPromptText(profile), app.getJobOffer(),
                    analyses.findByApplicationId(app.getId()).orElse(null));
        });

        // 2. AI e PDF (fuori transazione: possono volerci minuti)
        Path folder = properties.outputDir().resolve(slug(in.profile().getFullName())).resolve(folderName(in.offer()));
        String fileSuffix = slug(in.profile().getFullName());
        List<String> providers = new ArrayList<>();
        String cvPath = null, letterPath = null, coverLetter = null;

        if (cv) {
            String keywords = in.analysis() == null || in.analysis().getKeywords() == null ? null : String.join(", ", in.analysis().getKeywords());
            AiRouter.AiResult<TailoredCvResult> tailored = ai.tailorCv(in.profile(), in.profileText(), in.offer(), keywords);
            providers.add(tailored.provider());
            Map<String, Object> model = new HashMap<>();
            model.put("profile", in.profile());
            model.put("cv", tailored.value());
            cvPath = pdf.write(folder.resolve("CV_" + fileSuffix + ".pdf"), pdf.render("cv", model)).toString();
        }
        if (letter) {
            String summary = in.analysis() == null ? null : in.analysis().getSummary();
            AiRouter.AiResult<CoverLetterResult> result = ai.coverLetter(in.profile(), in.profileText(), in.offer(), summary);
            providers.add(result.provider());
            Map<String, Object> model = new HashMap<>();
            model.put("profile", in.profile());
            model.put("offer", in.offer());
            model.put("letter", result.value());
            model.put("today", LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM yyyy", java.util.Locale.ITALY)));
            letterPath = pdf.write(folder.resolve("Lettera_" + fileSuffix + ".pdf"), pdf.render("letter", model)).toString();
            coverLetter = result.value().toPlainText(in.profile().getFullName());
        }

        // 3. salvataggio (in transazione)
        final String fCv = cvPath, fLetter = letterPath, fText = coverLetter;
        return tx.execute(status -> {
            Application app = applications.findByIdAndProfileId(applicationId, profileId)
                    .orElseThrow(() -> JobPilotException.notFound("Candidatura " + applicationId + " non trovata"));
            if (fCv != null) app.setCvPath(fCv);
            if (fLetter != null) { app.setLetterPath(fLetter); app.setCoverLetter(fText); }
            if (app.getCvPath() != null && app.getCoverLetter() != null
                    && (app.getStatus() == ApplicationStatus.TROVATA || app.getStatus() == ApplicationStatus.DA_VALUTARE)) {
                app.setStatus(ApplicationStatus.PRONTA);
            }
            return new Generated(app.getCvPath(), app.getLetterPath(), app.getCoverLetter(), providers);
        });
    }

    /** Es. "2026-11-13_Acme_Junior-Java-Developer" */
    static String folderName(JobOffer offer) {
        String date = LocalDate.now().format(DateTimeFormatter.ISO_DATE);
        String company = slug(offer.getCompany() == null ? "azienda" : offer.getCompany());
        String title = slug(offer.getTitle());
        if (title.length() > 40) title = title.substring(0, 40);
        return date + "_" + company + "_" + title;
    }

    /** Toglie accenti e caratteri non validi nei nomi di file. */
    static String slug(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String cleaned = normalized.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return cleaned.isBlank() ? "documento" : cleaned;
    }
}
