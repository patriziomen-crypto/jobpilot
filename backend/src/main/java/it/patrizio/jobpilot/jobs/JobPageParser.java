package it.patrizio.jobpilot.jobs;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

/**
 * Estrae titolo, azienda, luogo e descrizione dall'HTML di una pagina di annuncio.
 * Prima prova il blocco JSON-LD "JobPosting" (standard, usato da LinkedIn e Indeed),
 * poi ricade su selettori specifici per sito, infine sul testo della pagina.
 */
@Component
public class JobPageParser {

    private static final Logger log = LoggerFactory.getLogger(JobPageParser.class);

    private final ObjectMapper mapper;

    public JobPageParser(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** Risultato dell'estrazione; i campi possono essere null se non trovati. */
    public record Parsed(String title, String company, String location, boolean remote, String description) {}

    public Parsed parse(String html, String url) {
        Document doc = Jsoup.parse(html, url == null ? "" : url);

        Optional<Parsed> fromJsonLd = parseJsonLd(doc);
        if (fromJsonLd.isPresent()) {
            return fromJsonLd.get();
        }

        JobSource source = JobSource.fromUrl(url);
        return switch (source) {
            case LINKEDIN -> parseLinkedIn(doc);
            case INDEED -> parseIndeed(doc);
            case ALTRO -> parseGeneric(doc);
        };
    }

    private Optional<Parsed> parseJsonLd(Document doc) {
        for (Element script : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode root = mapper.readTree(script.data());
                JsonNode posting = findJobPosting(root);
                if (posting == null) {
                    continue;
                }
                String title = text(posting, "title");
                String company = posting.path("hiringOrganization").path("name").asString(null);
                String location = extractLocation(posting.path("jobLocation"));
                boolean remote = "TELECOMMUTE".equalsIgnoreCase(posting.path("jobLocationType").asString(""));
                String descriptionHtml = text(posting, "description");
                String description = descriptionHtml == null ? null : htmlToText(descriptionHtml);
                if (title != null && description != null) {
                    return Optional.of(new Parsed(title, company, location, remote, description));
                }
            } catch (Exception e) {
                log.debug("JSON-LD non leggibile: {}", e.getMessage());
            }
        }
        return Optional.empty();
    }

    private JsonNode findJobPosting(JsonNode node) {
        if (node == null) return null;
        if (node.isArray()) {
            for (JsonNode n : node) {
                JsonNode found = findJobPosting(n);
                if (found != null) return found;
            }
            return null;
        }
        if (node.isObject()) {
            if ("JobPosting".equals(node.path("@type").asString(""))) {
                return node;
            }
            JsonNode graph = node.get("@graph");
            if (graph != null) {
                return findJobPosting(graph);
            }
        }
        return null;
    }

    private String extractLocation(JsonNode jobLocation) {
        if (jobLocation == null || jobLocation.isMissingNode()) return null;
        JsonNode first = jobLocation.isArray() && !jobLocation.isEmpty() ? jobLocation.get(0) : jobLocation;
        JsonNode address = first.path("address");
        StringBuilder sb = new StringBuilder();
        for (String key : new String[]{"addressLocality", "addressRegion", "addressCountry"}) {
            String v = address.path(key).asString(null);
            if (v != null && !v.isBlank()) {
                if (!sb.isEmpty()) sb.append(", ");
                sb.append(v);
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }

    private Parsed parseLinkedIn(Document doc) {
        String title = firstText(doc, "h1.top-card-layout__title", "h1.topcard__title", "h1");
        String company = firstText(doc, "a.topcard__org-name-link", ".top-card-layout__card .topcard__flavor a", ".topcard__flavor");
        String location = firstText(doc, ".topcard__flavor--bullet", ".top-card-layout__second-subline span");
        String description = firstText(doc, ".show-more-less-html__markup", ".description__text", "main");
        return new Parsed(title, company, location, false, description);
    }

    private Parsed parseIndeed(Document doc) {
        String title = firstText(doc, "h1.jobsearch-JobInfoHeader-title", "h1[data-testid=jobsearch-JobInfoHeader-title]", "h1");
        String company = firstText(doc, "[data-testid=inlineHeader-companyName]", ".jobsearch-InlineCompanyRating div", "[data-company-name]");
        String location = firstText(doc, "[data-testid=inlineHeader-companyLocation]", "[data-testid=jobsearch-JobInfoHeader-companyLocation]");
        String description = firstText(doc, "#jobDescriptionText", "main");
        return new Parsed(title, company, location, false, description);
    }

    private Parsed parseGeneric(Document doc) {
        String title = firstText(doc, "h1");
        if (title == null) title = doc.title();
        String description = firstText(doc, "main", "article", "body");
        return new Parsed(title, null, null, false, description);
    }

    private String firstText(Document doc, String... selectors) {
        for (String selector : selectors) {
            Element el = doc.selectFirst(selector);
            if (el != null) {
                String t = el.wholeText().strip();
                if (!t.isBlank()) return normalizeWhitespace(t);
            }
        }
        return null;
    }

    private String text(JsonNode node, String field) {
        String v = node.path(field).asString(null);
        return v == null || v.isBlank() ? null : v.strip();
    }

    /** Converte HTML in testo semplice mantenendo gli a capo di paragrafi e liste. */
    public String htmlToText(String html) {
        Document d = Jsoup.parseBodyFragment(html);
        d.outputSettings().prettyPrint(false);
        d.select("br").append("\\n");
        d.select("p, li, div, h1, h2, h3, h4").append("\\n");
        String text = d.text().replace("\\n", "\n");
        return normalizeWhitespace(text);
    }

    private String normalizeWhitespace(String text) {
        return text.replace(" ", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .strip();
    }
}
