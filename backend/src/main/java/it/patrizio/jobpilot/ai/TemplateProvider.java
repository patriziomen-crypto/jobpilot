package it.patrizio.jobpilot.ai;

import it.patrizio.jobpilot.jobs.JobOffer;
import it.patrizio.jobpilot.profile.Education;
import it.patrizio.jobpilot.profile.Experience;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.Skill;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Modalità base senza intelligenza artificiale: confronto di parole chiave, CV riordinato e lettera da modello.
 * Sempre disponibile, gratis: è la rete di sicurezza quando nessun fornitore AI funziona.
 */
public class TemplateProvider implements AiProvider {

    public static final String NAME = "Modalità base (senza AI)";

    /** Tecnologie e termini riconosciuti negli annunci IT. */
    private static final List<String> TECH_TERMS = List.of(
            "java", "spring", "spring boot", "angular", "react", "vue", "javascript", "typescript", "node", "html", "css",
            "sql", "mysql", "postgresql", "oracle", "mongodb", "sql server", "git", "github", "gitlab", "docker", "kubernetes",
            "aws", "azure", "gcp", "linux", "rest", "api", "microservizi", "microservices", "python", "c#", ".net", "php",
            "laravel", "kotlin", "swift", "android", "ios", "flutter", "c++", "cobol", "sap", "abap", "salesforce",
            "jenkins", "maven", "gradle", "junit", "selenium", "agile", "scrum", "jira", "hibernate", "jpa", "bootstrap",
            "tailwind", "postman", "intellij", "eclipse", "vs code", "excel", "power bi", "machine learning", "ai",
            "intelligenza artificiale", "cybersecurity", "cloud", "devops", "testing", "qa");

    /** "3 anni di esperienza", "5+ years of experience", "esperienza di almeno 2 anni": gli anni contano solo se legati all'esperienza. */
    private static final Pattern YEARS = Pattern.compile(
            "(\\d{1,2})\\s*\\+?\\s*(?:anni|years)\\W{0,3}(?:di\\s+|of\\s+)?(?:comprovata\\s+|solid\\s+|proven\\s+)?(?:esperienza|experience)"
                    + "|(?:esperienza|experience)\\W(?:[^.\\n]{0,40}?)(\\d{1,2})\\s*\\+?\\s*(?:anni|years)",
            Pattern.CASE_INSENSITIVE);

    private final Profile profile;

    public TemplateProvider(Profile profile) {
        this.profile = profile;
    }

    @Override
    public String name() {
        return NAME;
    }

    @Override
    public String model() {
        return "regole";
    }

    @Override
    public String ping() {
        return "La modalità base è sempre disponibile";
    }

    @Override
    public JobAnalysisResult analyze(String profileText, JobOffer offer) {
        String text = (offer.getTitle() + "\n" + offer.getDescription()).toLowerCase(Locale.ROOT);
        String language = detectLanguage(text);

        Set<String> mySkills = new LinkedHashSet<>();
        for (Skill s : profile.getSkills()) mySkills.add(s.getName().toLowerCase(Locale.ROOT));

        List<String> keywords = new ArrayList<>();
        for (String term : TECH_TERMS) {
            if (containsWord(text, term)) keywords.add(term);
        }

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        for (String k : keywords) {
            boolean have = mySkills.stream().anyMatch(s -> s.contains(k) || k.contains(s));
            (have ? matched : missing).add(capitalize(k));
        }

        List<String> redFlags = new ArrayList<>();
        Matcher m = YEARS.matcher(text);
        int maxYears = 0;
        while (m.find()) {
            String n = m.group(1) != null ? m.group(1) : m.group(2);
            if (n != null) maxYears = Math.max(maxYears, Integer.parseInt(n));
        }
        if (maxYears >= 3) redFlags.add("Richiesti " + maxYears + " anni di esperienza");
        if (containsWord(text, "senior") || containsWord(text, "lead") || containsWord(text, "architect")) {
            redFlags.add("Posizione di livello senior");
        }
        if (text.contains("partita iva") || text.contains("p.iva") || text.contains("p. iva")) {
            redFlags.add("Contratto a partita IVA");
        }
        boolean junior = containsWord(text, "junior") || text.contains("stage") || text.contains("tirocin")
                || text.contains("neolaureat") || text.contains("entry level") || text.contains("prima esperienza")
                || text.contains("academy") || text.contains("graduate");

        int score = 40 + Math.min(matched.size(), 5) * 9 - Math.min(missing.size(), 5) * 6 - redFlags.size() * 15 + (junior ? 12 : 0);
        if (keywords.isEmpty()) score -= 10; // annuncio probabilmente non IT
        score = Math.max(5, Math.min(95, score));

        String summary = "Valutazione automatica (senza AI): " + matched.size() + " competenze del profilo compaiono nell'annuncio"
                + (missing.isEmpty() ? "" : ", mancano " + missing.size())
                + (junior ? "; l'annuncio è rivolto a profili junior" : "")
                + (redFlags.isEmpty() ? "." : "; attenzione: " + String.join(", ", redFlags).toLowerCase(Locale.ROOT) + ".")
                + " Per una valutazione più accurata configura un fornitore AI nelle Impostazioni.";

        return new JobAnalysisResult(score, summary, matched, missing,
                keywords.stream().map(TemplateProvider::capitalize).toList(), redFlags, language);
    }

    @Override
    public TailoredCvResult tailorCv(String profileText, JobOffer offer, String keywords) {
        String text = (offer.getTitle() + "\n" + offer.getDescription()).toLowerCase(Locale.ROOT);

        List<TailoredCvResult.ExperienceOut> experiences = new ArrayList<>();
        for (Experience e : profile.getExperiences()) {
            experiences.add(new TailoredCvResult.ExperienceOut(e.getCompany(), e.getRole(), e.getPeriodLabel(), lines(e.getBullets())));
        }
        List<TailoredCvResult.EducationOut> educations = new ArrayList<>();
        for (Education e : profile.getEducations()) {
            educations.add(new TailoredCvResult.EducationOut(e.getInstitution(), e.getTitle(), e.getPeriodLabel(), lines(e.getDetails())));
        }
        // Competenze per categoria, con quelle citate nell'annuncio per prime
        Map<String, List<String>> groups = new LinkedHashMap<>();
        for (Skill s : profile.getSkills()) {
            groups.computeIfAbsent(s.getCategory(), k -> new ArrayList<>()).add(s.getName());
        }
        List<TailoredCvResult.SkillGroupOut> skillGroups = new ArrayList<>();
        for (Map.Entry<String, List<String>> g : groups.entrySet()) {
            List<String> ordered = new ArrayList<>(g.getValue());
            ordered.sort((a, b) -> Boolean.compare(!containsWord(text, a.toLowerCase(Locale.ROOT)), !containsWord(text, b.toLowerCase(Locale.ROOT))));
            skillGroups.add(new TailoredCvResult.SkillGroupOut(g.getKey(), ordered));
        }
        return new TailoredCvResult(profile.getHeadline(), profile.getSummary(), experiences, educations, skillGroups);
    }

    @Override
    public CoverLetterResult coverLetter(String profileText, JobOffer offer, String analysisSummary) {
        String text = (offer.getTitle() + "\n" + offer.getDescription()).toLowerCase(Locale.ROOT);
        boolean english = "en".equals(detectLanguage(text));
        String company = offer.getCompany() == null ? (english ? "your company" : "la vostra azienda") : offer.getCompany();
        List<String> matched = new ArrayList<>();
        for (Skill s : profile.getSkills()) {
            if (containsWord(text, s.getName().toLowerCase(Locale.ROOT))) matched.add(s.getName());
        }
        String skills = matched.isEmpty() ? null : String.join(", ", matched.subList(0, Math.min(5, matched.size())));

        if (english) {
            return new CoverLetterResult(
                    "Application for " + offer.getTitle(),
                    "Dear Hiring Team at " + company + ",",
                    List.of(
                            "I am writing to apply for the " + offer.getTitle() + " position at " + company + ". The role matches the path I am building as " + profile.getHeadline() + ".",
                            (skills == null
                                    ? "My training and projects gave me a solid base I am eager to put to use in your team."
                                    : "Among the requirements you list, I can already contribute with " + skills + ", which I have practised in my training and projects."),
                            "I am aware that I am at the beginning of my career: what I bring is fast learning, reliability and a real interest in growing with the company.",
                            "I am available for an interview at your convenience and would be glad to tell you more about myself."),
                    "Kind regards,");
        }
        return new CoverLetterResult(
                "Candidatura per la posizione di " + offer.getTitle(),
                "Gentile team di selezione di " + company + ",",
                List.of(
                        "scrivo per candidarmi alla posizione di " + offer.getTitle() + " presso " + company + ". Il ruolo è in linea con il percorso che sto costruendo come " + profile.getHeadline() + ".",
                        (skills == null
                                ? "La mia formazione e i progetti svolti mi hanno dato una base solida che desidero mettere al servizio del vostro team."
                                : "Tra i requisiti che indicate posso già contribuire con " + skills + ", che ho messo in pratica durante la formazione e i progetti svolti."),
                        "Sono consapevole di essere all'inizio del mio percorso: quello che porto è rapidità di apprendimento, affidabilità e un interesse concreto a crescere insieme all'azienda.",
                        "Resto a disposizione per un colloquio in qualsiasi momento e sarei felice di raccontarvi di più su di me."),
                "Cordiali saluti,");
    }

    static String detectLanguage(String text) {
        int it = count(text, " il ", " di ", " per ", " con ", " che ", " della ", " nel ", " una ");
        int en = count(text, " the ", " and ", " with ", " for ", " you ", " our ", " will ", " are ");
        return en > it ? "en" : "it";
    }

    private static int count(String text, String... words) {
        int n = 0;
        for (String w : words) {
            int idx = 0;
            while ((idx = text.indexOf(w, idx)) >= 0) { n++; idx += w.length(); }
        }
        return n;
    }

    private static boolean containsWord(String text, String term) {
        if (term == null || term.isBlank()) return false;
        String t = term.toLowerCase(Locale.ROOT);
        int idx = text.indexOf(t);
        while (idx >= 0) {
            boolean startOk = idx == 0 || !Character.isLetterOrDigit(text.charAt(idx - 1));
            int end = idx + t.length();
            boolean endOk = end >= text.length() || !Character.isLetterOrDigit(text.charAt(end));
            if (startOk && endOk) return true;
            idx = text.indexOf(t, idx + 1);
        }
        return false;
    }

    private static List<String> lines(String value) {
        if (value == null) return List.of();
        List<String> out = new ArrayList<>();
        for (String l : value.split("\\R")) if (!l.isBlank()) out.add(l.strip());
        return out;
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }
}
