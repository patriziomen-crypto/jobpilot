package it.patrizio.jobpilot.jobs;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/** Test unitari (senza database) sulla normalizzazione degli URL, che decide i duplicati. */
class JobImportServiceTest {

    @Test
    void linkedinConTitoloEParametriDiTracciamento() {
        String url = "https://www.linkedin.com/jobs/view/junior-java-developer-at-acme-4123456789/?refId=abc&trackingId=xyz";
        assertEquals("https://www.linkedin.com/jobs/view/4123456789", JobImportService.normalizeUrl(url));
    }

    @Test
    void linkedinDallaRicercaConCurrentJobId() {
        String url = "https://www.linkedin.com/jobs/search/?currentJobId=4123456789&keywords=java";
        assertEquals("https://www.linkedin.com/jobs/view/4123456789", JobImportService.normalizeUrl(url));
    }

    @Test
    void indeedTieneSoloIlParametroJk() {
        String url = "https://it.indeed.com/viewjob?jk=0a1b2c3d4e5f&from=serp&vjs=3";
        assertEquals("https://it.indeed.com/viewjob?jk=0a1b2c3d4e5f", JobImportService.normalizeUrl(url));
    }

    @Test
    void altriSitiPerdonoQueryEFrammento() {
        String url = "https://careers.example.com/jobs/123/?utm_source=linkedin#apply";
        assertEquals("https://careers.example.com/jobs/123", JobImportService.normalizeUrl(url));
    }

    @Test
    void hashDiversiPerUrlDiversi() {
        assertNotEquals(JobImportService.sha256("a"), JobImportService.sha256("b"));
        assertEquals(64, JobImportService.sha256("a").length());
    }
}
