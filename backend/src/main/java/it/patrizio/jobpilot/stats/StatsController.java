package it.patrizio.jobpilot.stats;

import it.patrizio.jobpilot.applications.Application;
import it.patrizio.jobpilot.applications.ApplicationRepository;
import it.patrizio.jobpilot.applications.ApplicationStatus;
import it.patrizio.jobpilot.jobs.JobAnalysis;
import it.patrizio.jobpilot.jobs.JobAnalysisRepository;
import it.patrizio.jobpilot.jobs.JobSource;
import it.patrizio.jobpilot.profile.ProfileContext;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Numeri sulla ricerca di lavoro della persona attiva: stati, fonti, invii per settimana, tasso di risposta, promemoria. */
@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private static final int SETTIMANE = 8;
    private static final Set<ApplicationStatus> CON_RISPOSTA = Set.of(
            ApplicationStatus.RISPOSTA, ApplicationStatus.COLLOQUIO, ApplicationStatus.OFFERTA, ApplicationStatus.RIFIUTATA);
    private static final Set<ApplicationStatus> INVIATE = Set.of(
            ApplicationStatus.INVIATA, ApplicationStatus.RISPOSTA, ApplicationStatus.COLLOQUIO,
            ApplicationStatus.OFFERTA, ApplicationStatus.RIFIUTATA);

    private final ApplicationRepository applications;
    private final JobAnalysisRepository analyses;
    private final ProfileContext profileContext;

    public StatsController(ApplicationRepository applications, JobAnalysisRepository analyses, ProfileContext profileContext) {
        this.applications = applications;
        this.analyses = analyses;
        this.profileContext = profileContext;
    }

    public record Settimana(String inizio, long inviate, long trovate) {}

    public record Promemoria(Long applicationId, String title, String company, String tipo, String data) {}

    public record FontePerformance(JobSource source, long totali, long inviate, long risposte, Double punteggioMedio) {}

    public record Stats(
            long totali,
            Map<ApplicationStatus, Long> perStato,
            List<FontePerformance> perFonte,
            long inviate,
            long conRisposta,
            double tassoRisposta,
            long colloqui,
            Double punteggioMedio,
            long nonAnalizzate,
            List<Settimana> settimane,
            List<Promemoria> promemoria
    ) {}

    @GetMapping
    @Transactional(readOnly = true)
    public Stats stats() {
        Long profileId = profileContext.currentProfileId();
        List<Application> tutte = applications.findAllByProfileIdOrderByUpdatedAtDesc(profileId);
        Map<Long, JobAnalysis> analisiPerCandidatura = new HashMap<>();
        for (JobAnalysis a : analyses.findAllByApplicationProfileId(profileId)) {
            analisiPerCandidatura.put(a.getApplication().getId(), a);
        }

        Map<ApplicationStatus, Long> perStato = new EnumMap<>(ApplicationStatus.class);
        for (ApplicationStatus s : ApplicationStatus.values()) perStato.put(s, 0L);
        long inviate = 0, conRisposta = 0, colloqui = 0, nonAnalizzate = 0;
        double sommaPunteggi = 0;
        int conPunteggio = 0;

        Map<JobSource, long[]> fonte = new EnumMap<>(JobSource.class);
        Map<JobSource, double[]> fontePunteggi = new EnumMap<>(JobSource.class);
        for (JobSource s : JobSource.values()) {
            fonte.put(s, new long[3]);
            fontePunteggi.put(s, new double[2]);
        }

        LocalDate oggi = LocalDate.now();
        LocalDate inizioFinestra = oggi.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(SETTIMANE - 1);
        long[] inviatePerSettimana = new long[SETTIMANE];
        long[] trovatePerSettimana = new long[SETTIMANE];
        List<Promemoria> promemoria = new ArrayList<>();

        for (Application app : tutte) {
            perStato.merge(app.getStatus(), 1L, Long::sum);
            JobSource src = app.getJobOffer().getSource();
            long[] f = fonte.get(src);
            f[0]++;
            if (INVIATE.contains(app.getStatus())) { inviate++; f[1]++; }
            if (CON_RISPOSTA.contains(app.getStatus())) { conRisposta++; f[2]++; }
            if (app.getStatus() == ApplicationStatus.COLLOQUIO || app.getStatus() == ApplicationStatus.OFFERTA) colloqui++;

            JobAnalysis analisi = analisiPerCandidatura.get(app.getId());
            if (analisi == null) {
                nonAnalizzate++;
                if (app.getStatus() == ApplicationStatus.TROVATA) {
                    promemoria.add(new Promemoria(app.getId(), app.getJobOffer().getTitle(), app.getJobOffer().getCompany(),
                            "DA_ANALIZZARE", app.getJobOffer().getFoundAt().toLocalDate().toString()));
                }
            } else {
                sommaPunteggi += analisi.getScore();
                conPunteggio++;
                double[] fp = fontePunteggi.get(src);
                fp[0] += analisi.getScore();
                fp[1]++;
            }

            if (app.getStatus() == ApplicationStatus.INVIATA && app.getFollowUpAt() != null && !app.getFollowUpAt().isAfter(oggi)) {
                promemoria.add(new Promemoria(app.getId(), app.getJobOffer().getTitle(), app.getJobOffer().getCompany(),
                        "DA_RICONTATTARE", app.getFollowUpAt().toString()));
            }

            if (app.getSentAt() != null) {
                int idx = indiceSettimana(app.getSentAt().toLocalDate(), inizioFinestra);
                if (idx >= 0) inviatePerSettimana[idx]++;
            }
            int idxTrovata = indiceSettimana(app.getCreatedAt().toLocalDate(), inizioFinestra);
            if (idxTrovata >= 0) trovatePerSettimana[idxTrovata]++;
        }

        List<Settimana> settimane = new ArrayList<>();
        for (int i = 0; i < SETTIMANE; i++) {
            settimane.add(new Settimana(inizioFinestra.plusWeeks(i).toString(), inviatePerSettimana[i], trovatePerSettimana[i]));
        }

        List<FontePerformance> perFonte = new ArrayList<>();
        for (JobSource s : JobSource.values()) {
            long[] f = fonte.get(s);
            double[] fp = fontePunteggi.get(s);
            perFonte.add(new FontePerformance(s, f[0], f[1], f[2], fp[1] == 0 ? null : Math.round(fp[0] / fp[1] * 10) / 10.0));
        }

        promemoria.sort((a, b) -> a.data().compareTo(b.data()));

        return new Stats(
                tutte.size(), perStato, perFonte, inviate, conRisposta,
                inviate == 0 ? 0 : Math.round(conRisposta * 1000.0 / inviate) / 10.0,
                colloqui,
                conPunteggio == 0 ? null : Math.round(sommaPunteggi / conPunteggio * 10) / 10.0,
                nonAnalizzate, settimane, promemoria);
    }

    private static int indiceSettimana(LocalDate data, LocalDate inizioFinestra) {
        if (data.isBefore(inizioFinestra)) return -1;
        long giorni = ChronoUnit.DAYS.between(inizioFinestra, data);
        int idx = (int) (giorni / 7);
        return idx >= SETTIMANE ? -1 : idx;
    }
}
