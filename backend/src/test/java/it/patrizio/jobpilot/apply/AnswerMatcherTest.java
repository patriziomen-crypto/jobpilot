package it.patrizio.jobpilot.apply;

import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.StandardAnswer;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Le domande dei moduli di candidatura devono trovare la risposta pronta giusta, o una regola sensata. */
class AnswerMatcherTest {

    private static Profile profilo() {
        Profile p = new Profile();
        p.setFullName("Patrizio Menegoni");
        p.setPhone("+39 000 000 0000");
        p.setEmail("nome@esempio.it");
        p.setLocation("Pomezia (RM)");
        aggiungi(p, "Anni di esperienza come sviluppatore", "0-1 (tirocinio di 140 ore + progetti personali)");
        aggiungi(p, "Disponibilità a iniziare", "Immediata");
        aggiungi(p, "Autorizzazione a lavorare in Italia", "Sì, cittadino italiano");
        aggiungi(p, "Livello di inglese", "Base (A2/B1), in miglioramento");
        return p;
    }

    private static void aggiungi(Profile p, String domanda, String risposta) {
        StandardAnswer a = new StandardAnswer();
        a.setProfile(p);
        a.setQuestion(domanda);
        a.setAnswer(risposta);
        p.getStandardAnswers().add(a);
    }

    @Test
    void trovaLaRispostaProntaPerParoleInComune() {
        AnswerMatcher m = new AnswerMatcher(profilo());
        assertEquals("Immediata", m.answer("Qual è la tua disponibilità a iniziare?", null));
        assertEquals("Sì, cittadino italiano", m.answer("Sei autorizzato a lavorare in Italia?", null));
    }

    @Test
    void conLeOpzioniSceglieQuellaPiuSimile() {
        AnswerMatcher m = new AnswerMatcher(profilo());
        assertEquals("Sì", m.answer("Sei autorizzato a lavorare in Italia?", List.of("Sì", "No")));
        assertEquals("0-1", m.answer("Quanti anni di esperienza hai con Java?", List.of("0-1", "2-4", "5+")));
        assertEquals("Yes", m.answer("Do you have a driver's license?", List.of("Yes", "No")));
    }

    @Test
    void campiAnagraficiDalProfilo() {
        AnswerMatcher m = new AnswerMatcher(profilo());
        assertEquals("+39 000 000 0000", m.answer("Numero di telefono", null));
        assertEquals("nome@esempio.it", m.answer("Indirizzo email", null));
        assertEquals("Patrizio", m.answer("Nome", null));
        assertEquals("Menegoni", m.answer("Cognome", null));
    }

    @Test
    void domandaSconosciutaRestituisceNull() {
        AnswerMatcher m = new AnswerMatcher(profilo());
        assertNull(m.answer("Descrivi un progetto di cui vai fiero", null));
    }
}
