package it.patrizio.jobpilot.documents;

import it.patrizio.jobpilot.ai.CoverLetterResult;
import it.patrizio.jobpilot.ai.TailoredCvResult;
import it.patrizio.jobpilot.jobs.JobOffer;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.profile.ProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifica che i template Thymeleaf diventino PDF validi (senza chiamare Claude). */
@SpringBootTest
class PdfServiceTest {

    @Autowired
    PdfService pdf;

    @Autowired
    ProfileService profileService;

    @Test
    void generaCvInPdf() {
        Profile profile = profileService.getProfile();
        TailoredCvResult cv = new TailoredCvResult(
                "Sviluppatore Junior Java",
                "Profilo riscritto per l'offerta, con accenti: perché, più, così.",
                List.of(new TailoredCvResult.ExperienceOut("EVOCONS", "Tirocinio Sviluppatore Software", "140 ore",
                        List.of("Supporto allo sviluppo nel team tecnico", "Applicazione pratica di Java e MySQL"))),
                List.of(new TailoredCvResult.EducationOut("Accademia Informatica", "Programmatore Full Stack Junior", null,
                        List.of("OOP, database, testing"))),
                List.of(new TailoredCvResult.SkillGroupOut("Linguaggi & Web", List.of("Java", "Angular", "MySQL"))));

        byte[] bytes = pdf.render("cv", Map.of("profile", profile, "cv", cv));
        assertTrue(bytes.length > 1000, "PDF troppo piccolo");
        assertTrue(new String(bytes, 0, 5).startsWith("%PDF"), "non è un PDF");
    }

    @Test
    void generaLetteraInPdf() {
        Profile profile = profileService.getProfile();
        JobOffer offer = new JobOffer();
        offer.setTitle("Junior Java Developer");
        offer.setCompany("Acme S.r.l.");
        offer.setLocation("Roma");
        CoverLetterResult letter = new CoverLetterResult(
                "Candidatura per Junior Java Developer",
                "Gentile team di selezione di Acme,",
                List.of("Primo paragrafo con l'aggancio all'annuncio.", "Secondo paragrafo con le competenze vere.",
                        "Terzo paragrafo: disponibilità e chiusura."),
                "Cordiali saluti,");

        byte[] bytes = pdf.render("letter", Map.of("profile", profile, "offer", offer, "letter", letter, "today", "13 novembre 2026"));
        assertTrue(bytes.length > 1000, "PDF troppo piccolo");
        assertTrue(new String(bytes, 0, 5).startsWith("%PDF"), "non è un PDF");
    }
}
