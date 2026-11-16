package it.patrizio.jobpilot.jobs;

import it.patrizio.jobpilot.config.JobPilotException;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Optional;

/** Importa un'offerta da URL e/o testo incollato, evitando i duplicati. */
@Service
public class JobImportService {

    private static final Logger log = LoggerFactory.getLogger(JobImportService.class);
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";

    private final JobOfferRepository repository;
    private final JobPageParser parser;

    public JobImportService(JobOfferRepository repository, JobPageParser parser) {
        this.repository = repository;
        this.parser = parser;
    }

    /** Esito dell'import: l'offerta e se esisteva già. */
    public record ImportResult(JobOffer offer, boolean alreadyExisted) {}

    @Transactional
    public ImportResult importJob(JobImportRequest request) {
        if (!request.hasUrl() && !request.hasText()) {
            throw JobPilotException.badRequest("Inserisci l'URL dell'annuncio oppure incolla il suo testo");
        }

        String normalizedUrl = request.hasUrl() ? normalizeUrl(request.url()) : null;
        String hash = normalizedUrl == null ? null : sha256(normalizedUrl);
        if (hash != null) {
            Optional<JobOffer> existing = repository.findByUrlHash(hash);
            if (existing.isPresent()) {
                return new ImportResult(existing.get(), true);
            }
        }

        JobPageParser.Parsed parsed = null;
        if (request.hasText()) {
            // Il testo incollato può essere HTML (dal bookmarklet) o testo semplice
            String text = request.text().strip();
            boolean looksLikeHtml = text.startsWith("<") && text.contains("</");
            parsed = looksLikeHtml
                    ? parser.parse(text, normalizedUrl)
                    : new JobPageParser.Parsed(null, null, null, false, text);
        } else {
            parsed = fetchAndParse(normalizedUrl);
        }

        String description = firstNonBlank(parsed.description(), request.text());
        if (description == null || description.isBlank()) {
            throw new JobPilotException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Non sono riuscito a leggere l'annuncio da quell'indirizzo: incolla il testo dell'offerta");
        }

        JobOffer offer = new JobOffer();
        offer.setSource(JobSource.fromUrl(normalizedUrl));
        offer.setUrl(normalizedUrl);
        offer.setUrlHash(hash);
        offer.setTitle(truncate(firstNonBlank(request.title(), parsed.title(), guessTitle(description)), 200));
        offer.setCompany(truncate(firstNonBlank(request.company(), parsed.company()), 160));
        offer.setLocation(truncate(firstNonBlank(request.location(), parsed.location()), 160));
        offer.setRemote(request.remote() != null ? request.remote() : parsed.remote() || mentionsRemote(description));
        offer.setDescription(description);
        return new ImportResult(repository.save(offer), false);
    }

    private JobPageParser.Parsed fetchAndParse(String url) {
        try {
            String current = url;
            Connection.Response response = null;
            // Redirect seguiti a mano (max 5) perché ogni salto va ricontrollato: solo http/https verso host pubblici
            for (int hop = 0; hop < 5; hop++) {
                assertPublicUrl(current);
                response = Jsoup.connect(current)
                        .userAgent(USER_AGENT)
                        .header("Accept-Language", "it-IT,it;q=0.9,en;q=0.8")
                        .timeout(15_000)
                        .followRedirects(false)
                        .ignoreHttpErrors(true)
                        .execute();
                String location = response.header("Location");
                if (response.statusCode() / 100 == 3 && location != null && !location.isBlank()) {
                    current = response.url().toURI().resolve(location).toString();
                    continue;
                }
                break;
            }
            if (response.statusCode() >= 400) {
                log.warn("Download annuncio fallito: HTTP {} per {}", response.statusCode(), url);
                throw new JobPilotException(HttpStatus.UNPROCESSABLE_CONTENT,
                        "Il sito ha risposto con errore " + response.statusCode()
                                + " (spesso Indeed e LinkedIn bloccano il download automatico): incolla il testo dell'annuncio");
            }
            return parser.parse(response.body(), url);
        } catch (IOException | URISyntaxException e) {
            log.warn("Download annuncio fallito per {}: {}", url, e.getMessage());
            throw new JobPilotException(HttpStatus.UNPROCESSABLE_CONTENT,
                    "Non riesco a scaricare la pagina (" + e.getMessage() + "): incolla il testo dell'annuncio");
        }
    }

    /**
     * Il backend scarica solo pagine web pubbliche: niente file://, niente indirizzi del PC o della rete
     * locale (il browser di un sito esterno non deve poter usare JobPilot per leggere servizi interni).
     */
    static void assertPublicUrl(String url) {
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException e) {
            throw JobPilotException.badRequest("L'indirizzo dell'annuncio non è valido");
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            throw JobPilotException.badRequest("Sono ammessi solo indirizzi http e https");
        }
        String host = uri.getHost();
        if (host == null || host.isBlank() || uri.getUserInfo() != null) {
            throw JobPilotException.badRequest("L'indirizzo dell'annuncio non è valido");
        }
        try {
            for (java.net.InetAddress address : java.net.InetAddress.getAllByName(host)) {
                if (address.isLoopbackAddress() || address.isSiteLocalAddress() || address.isLinkLocalAddress()
                        || address.isAnyLocalAddress() || address.isMulticastAddress()) {
                    throw JobPilotException.badRequest("Gli indirizzi locali o di rete interna non sono ammessi");
                }
            }
        } catch (java.net.UnknownHostException e) {
            throw new JobPilotException(HttpStatus.UNPROCESSABLE_CONTENT, "Sito non trovato: " + host);
        }
    }

    /** Toglie parametri di tracciamento e frammenti, così lo stesso annuncio ha sempre lo stesso URL. */
    public static String normalizeUrl(String raw) {
        String url = raw.strip();
        try {
            URI uri = new URI(url);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
            String path = uri.getPath() == null ? "" : uri.getPath().replaceAll("/+$", "");
            String query = uri.getQuery();

            if (host.contains("indeed.") && query != null) {
                // Su Indeed l'identificativo dell'annuncio è il parametro jk
                for (String part : query.split("&")) {
                    if (part.startsWith("jk=")) {
                        return "https://" + host + "/viewjob?" + part;
                    }
                }
            }
            if (host.contains("linkedin.com")) {
                // /jobs/view/<id>/ oppure /jobs/view/<titolo>-<id>/
                java.util.regex.Matcher m = java.util.regex.Pattern.compile("/jobs/view/(?:[^/]*?-)?(\\d+)").matcher(path);
                if (m.find()) {
                    return "https://www.linkedin.com/jobs/view/" + m.group(1);
                }
                if (query != null) {
                    for (String part : query.split("&")) {
                        if (part.startsWith("currentJobId=")) {
                            return "https://www.linkedin.com/jobs/view/" + part.substring("currentJobId=".length());
                        }
                    }
                }
            }
            String scheme = uri.getScheme() == null ? "https" : uri.getScheme();
            return scheme + "://" + host + path;
        } catch (URISyntaxException e) {
            throw JobPilotException.badRequest("L'indirizzo dell'annuncio non è valido");
        }
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String guessTitle(String description) {
        String firstLine = description.lines().map(String::strip).filter(l -> !l.isBlank()).findFirst().orElse("Offerta senza titolo");
        return firstLine.length() > 120 ? firstLine.substring(0, 117) + "..." : firstLine;
    }

    private static boolean mentionsRemote(String description) {
        String d = description.toLowerCase();
        return d.contains("full remote") || d.contains("100% remoto") || d.contains("da remoto") || d.contains("smart working");
    }

    private static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.strip();
        }
        return null;
    }

    private static String truncate(String value, int max) {
        if (value == null) return null;
        return value.length() <= max ? value : value.substring(0, max - 1) + "…";
    }
}
