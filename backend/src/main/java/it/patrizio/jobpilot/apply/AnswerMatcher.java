package it.patrizio.jobpilot.apply;

import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.StandardAnswer;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Trova la risposta a una domanda del modulo di candidatura usando le "risposte pronte" del profilo
 * e qualche regola di buon senso. Restituisce null se non sa rispondere.
 */
public class AnswerMatcher {

    private static final Set<String> STOP = Set.of("di", "la", "il", "le", "a", "in", "e", "the", "of", "to", "da", "del",
            "della", "dei", "per", "con", "un", "una", "you", "your", "do", "have", "are", "sei", "hai", "quanti", "quanto", "qual",
            "quale", "how", "many", "what", "is", "it", "che", "al", "alla", "nel", "su", "o", "or", "and", "lo", "ha");

    private final Profile profile;

    public AnswerMatcher(Profile profile) {
        this.profile = profile;
    }

    /** Risposta testuale a una domanda; le opzioni (per tendine e scelte) aiutano a scegliere. */
    public String answer(String question, List<String> options) {
        String q = normalize(question);
        if (q.isBlank()) return null;

        // 1. risposte pronte del profilo, per sovrapposizione di parole
        String best = null;
        int bestScore = 0;
        Set<String> qWords = words(q);
        for (StandardAnswer a : profile.getStandardAnswers()) {
            Set<String> aWords = words(normalize(a.getQuestion()));
            int common = 0;
            for (String w : aWords) if (qWords.contains(w)) common++;
            int score = common * 2 - Math.max(0, aWords.size() - common) / 2;
            if (common >= 2 && score > bestScore) {
                bestScore = score;
                best = a.getAnswer();
            }
        }
        if (best != null) return pickOption(best, options);

        // 2. regole generali
        if (q.matches(".*(anni|years).*(esperienz|experience).*") || q.matches(".*(esperienz|experience).*(anni|years).*")) {
            return pickOption("1", options);
        }
        if (q.contains("patente") || q.contains("driver") || q.contains("automunit")) return pickOption("Sì", options);
        if (q.contains("autorizz") && (q.contains("lavor") || q.contains("work"))) return pickOption("Sì", options);
        if (q.contains("sponsor") || q.contains("visa")) return pickOption("No", options);
        if (q.contains("inglese") || q.contains("english")) return pickOption("Base", options);
        if (q.contains("laurea") || q.contains("degree") || q.contains("bachelor")) return pickOption("No", options);
        if (q.contains("disponib") && (q.contains("subito") || q.contains("immediat") || q.contains("inizi") || q.contains("start"))) {
            return pickOption("Immediata", options);
        }
        if (q.contains("telefono") || q.contains("phone") || q.contains("cellulare")) return profile.getPhone();
        if (q.contains("email") || q.contains("e-mail")) return profile.getEmail();
        if (q.contains("citt") || q.contains("city") || q.contains("residen") || q.contains("location")) return profile.getLocation();
        if (q.contains("linkedin")) return profile.getLinkedinUrl();
        if (q.contains("github") || q.contains("portfolio") || q.contains("sito")) return profile.getGithubUrl();
        if (q.contains("cognome") || q.contains("last name") || q.contains("surname")) return lastName();
        if (q.matches(".*\\bnome\\b.*") && !q.contains("azienda") || q.contains("first name")) return firstName();
        if (q.contains("ral") || q.contains("retribuz") || q.contains("salary") || q.contains("stipendio")) {
            return pickOption("Da concordare", options);
        }
        return null;
    }

    /** Per tendine e scelte: cerca l'opzione più simile alla risposta; altrimenti la risposta com'è. */
    static String pickOption(String answer, List<String> options) {
        if (options == null || options.isEmpty()) return answer;
        String a = normalize(answer);
        for (String o : options) if (normalize(o).equals(a)) return o;
        for (String o : options) {
            String n = normalize(o);
            if (!n.isBlank() && (n.contains(a) || a.contains(n))) return o;
        }
        // sì/no in varie forme
        if (a.matches("si|sì|yes|y")) for (String o : options) if (normalize(o).matches("si|sì|yes|y")) return o;
        if (a.matches("no|n")) for (String o : options) if (normalize(o).matches("no|n")) return o;
        // numeri: l'opzione che contiene lo stesso numero, altrimenti la più bassa
        if (a.matches("\\d+(-\\d+)?.*")) {
            String num = a.replaceAll("[^0-9].*", "");
            for (String o : options) if (normalize(o).contains(num)) return o;
            for (String o : options) if (normalize(o).matches(".*\\b[01]\\b.*")) return o;
        }
        return null;
    }

    private String firstName() {
        String[] parts = profile.getFullName().trim().split("\\s+");
        return parts[0];
    }

    private String lastName() {
        String[] parts = profile.getFullName().trim().split("\\s+");
        return parts.length > 1 ? String.join(" ", Arrays.copyOfRange(parts, 1, parts.length)) : "";
    }

    static String normalize(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        return n.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9 ]+", " ").replaceAll("\\s+", " ").strip();
    }

    private static Set<String> words(String normalized) {
        Set<String> out = new HashSet<>();
        for (String w : normalized.split(" ")) {
            if (w.length() >= 3 && !STOP.contains(w)) out.add(w.length() > 6 ? w.substring(0, 6) : w);
        }
        return out;
    }

    /** Domande a cui l'app non ha saputo rispondere in una sessione (per mostrarle all'utente). */
    public static List<String> unanswered(List<String> questions, List<String> answers) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < questions.size(); i++) if (answers.get(i) == null) out.add(questions.get(i));
        return out;
    }
}
