package it.patrizio.jobpilot.search;

import it.patrizio.jobpilot.jobs.JobPageParser;
import it.patrizio.jobpilot.jobs.JobSource;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Ricerca su LinkedIn tramite le pagine pubbliche "guest" (visibili senza login):
 * la stessa lista che si vede aprendo linkedin.com/jobs da non loggati.
 */
@Component
public class LinkedInSearchClient {

    private static final Logger log = LoggerFactory.getLogger(LinkedInSearchClient.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
    private static final Pattern JOB_ID = Pattern.compile("(\\d{6,})");

    private final JobPageParser parser;

    public LinkedInSearchClient(JobPageParser parser) {
        this.parser = parser;
    }

    /** Pagina di risultati (25 per pagina). days limita agli annunci pubblicati negli ultimi N giorni. */
    public List<JobCard> search(String keywords, String location, int days, boolean remote, int start) throws IOException {
        StringBuilder url = new StringBuilder("https://www.linkedin.com/jobs-guest/jobs/api/seeMoreJobPostings/search?");
        url.append("keywords=").append(enc(keywords));
        url.append("&location=").append(enc(location));
        url.append("&f_TPR=r").append(Math.max(1, days) * 86400L);
        url.append("&f_E=1%2C2");          // livello: tirocinio, base
        url.append(remote ? "&f_WT=1%2C2%2C3" : "&f_WT=1"); // tutte le modalità, oppure solo in sede
        url.append("&start=").append(start);

        Document doc = get(url.toString());
        List<JobCard> cards = new ArrayList<>();
        for (Element card : doc.select(".base-search-card")) {
            String urn = card.attr("data-entity-urn");
            Matcher m = JOB_ID.matcher(urn);
            String id = m.find() ? m.group(1) : null;
            if (id == null) {
                Element link = card.selectFirst("a.base-card__full-link");
                if (link != null) {
                    Matcher m2 = JOB_ID.matcher(link.attr("href"));
                    if (m2.find()) id = m2.group(1);
                }
            }
            if (id == null) continue;
            cards.add(new JobCard(
                    JobSource.LINKEDIN,
                    "https://www.linkedin.com/jobs/view/" + id,
                    text(card, ".base-search-card__title"),
                    text(card, ".base-search-card__subtitle"),
                    text(card, ".job-search-card__location")));
        }
        log.info("LinkedIn \"{}\" @ {} (start {}): {} risultati", keywords, location, start, cards.size());
        return cards;
    }

    /** Testo completo dell'annuncio. Restituisce null se LinkedIn non lo rende disponibile. */
    public String description(String jobUrl) {
        Matcher m = JOB_ID.matcher(jobUrl);
        if (!m.find()) return null;
        try {
            Document doc = get("https://www.linkedin.com/jobs-guest/jobs/api/jobPosting/" + m.group(1));
            Element body = doc.selectFirst(".description__text, .show-more-less-html__markup");
            if (body == null) return null;
            StringBuilder sb = new StringBuilder(parser.htmlToText(body.html()));
            // Criteri (livello, tipo di impiego, settore): utili all'analisi
            for (Element item : doc.select(".description__job-criteria-item")) {
                String k = text(item, ".description__job-criteria-subheader");
                String v = text(item, ".description__job-criteria-text");
                if (k != null && v != null) sb.append('\n').append(k).append(": ").append(v);
            }
            return sb.toString().strip();
        } catch (IOException e) {
            log.warn("Dettaglio LinkedIn non letto per {}: {}", jobUrl, e.getMessage());
            return null;
        }
    }

    private Document get(String url) throws IOException {
        Connection.Response response = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                .timeout(20_000)
                .ignoreHttpErrors(true)
                .execute();
        if (response.statusCode() == 429) {
            throw new IOException("LinkedIn chiede di rallentare (429): riprova tra qualche minuto");
        }
        if (response.statusCode() >= 400) {
            throw new IOException("LinkedIn ha risposto " + response.statusCode());
        }
        return response.parse();
    }

    private static String text(Element root, String selector) {
        Element el = root.selectFirst(selector);
        if (el == null) return null;
        String t = el.text().replace(' ', ' ').strip();
        return t.isBlank() ? null : t;
    }

    private static String enc(String value) {
        return URLEncoder.encode(value == null ? "" : value, StandardCharsets.UTF_8);
    }
}
