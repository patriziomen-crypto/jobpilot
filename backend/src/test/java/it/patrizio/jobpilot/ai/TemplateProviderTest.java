package it.patrizio.jobpilot.ai;

import it.patrizio.jobpilot.jobs.JobOffer;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La modalità base deve riconoscere gli anni di esperienza richiesti, non quelli della storia aziendale. */
class TemplateProviderTest {

    private static Profile profilo() {
        Profile p = new Profile();
        p.setFullName("Mario Rossi");
        p.setHeadline("Sviluppatore Junior");
        for (String s : new String[]{"Java", "Angular", "MySQL", "Git"}) {
            Skill sk = new Skill();
            sk.setProfile(p);
            sk.setCategory("Linguaggi");
            sk.setName(s);
            p.getSkills().add(sk);
        }
        return p;
    }

    private static JobOffer offerta(String descrizione) {
        JobOffer o = new JobOffer();
        o.setTitle("Java Developer");
        o.setCompany("Acme");
        o.setDescription(descrizione);
        return o;
    }

    @Test
    void gliAnniDellaStoriaAziendaleNonSonoUnRedFlag() {
        TemplateProvider tp = new TemplateProvider(profilo());
        JobAnalysisResult r = tp.analyze("", offerta("Acme, da oltre 60 anni leader nel settore, cerca un junior Java developer con conoscenza di Java e Git."));
        assertTrue(r.redFlags().stream().noneMatch(f -> f.contains("anni")), "trovato: " + r.redFlags());
        assertTrue(r.matchedRequirements().contains("Java"));
        assertEquals("it", r.language());
    }

    @Test
    void gliAnniDiEsperienzaRichiestiSonoUnRedFlag() {
        TemplateProvider tp = new TemplateProvider(profilo());
        JobAnalysisResult r1 = tp.analyze("", offerta("Richiesti almeno 3 anni di esperienza in Java e Spring."));
        assertTrue(r1.redFlags().stream().anyMatch(f -> f.contains("3 anni")), "trovato: " + r1.redFlags());

        JobAnalysisResult r2 = tp.analyze("", offerta("We are looking for a developer with 5+ years of experience with Java and the Spring framework."));
        assertTrue(r2.redFlags().stream().anyMatch(f -> f.contains("5 anni")), "trovato: " + r2.redFlags());
        assertEquals("en", r2.language());

        JobAnalysisResult r3 = tp.analyze("", offerta("Esperienza comprovata di almeno 4 anni nello sviluppo backend."));
        assertTrue(r3.redFlags().stream().anyMatch(f -> f.contains("4 anni")), "trovato: " + r3.redFlags());
    }

    @Test
    void unAnnuncioJuniorNonHaRedFlagSugliAnni() {
        TemplateProvider tp = new TemplateProvider(profilo());
        JobAnalysisResult r = tp.analyze("", offerta("Cerchiamo uno stagista: nessuna esperienza richiesta, formazione interna di 2 mesi."));
        assertFalse(r.redFlags().stream().anyMatch(f -> f.contains("anni")));
        assertTrue(r.score() >= 40, "punteggio " + r.score());
    }
}
