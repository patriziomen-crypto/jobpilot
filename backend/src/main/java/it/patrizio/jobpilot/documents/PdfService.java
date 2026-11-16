package it.patrizio.jobpilot.documents;

import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import it.patrizio.jobpilot.config.JobPilotException;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;

/** Rende un template Thymeleaf in HTML e lo converte in PDF con OpenHTMLtoPDF. */
@Service
public class PdfService {

    private static final Logger log = LoggerFactory.getLogger(PdfService.class);
    private static final File WINDOWS_FONT = new File("C:/Windows/Fonts/calibri.ttf");
    private static final File WINDOWS_FONT_BOLD = new File("C:/Windows/Fonts/calibrib.ttf");

    private final TemplateEngine templateEngine;

    public PdfService(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /** Genera il PDF dal template (es. "cv", "letter") con le variabili del modello. */
    public byte[] render(String template, Map<String, Object> model) {
        Context context = new Context(Locale.ITALY);
        context.setVariables(model);
        String html = templateEngine.process(template, context);

        // OpenHTMLtoPDF vuole XHTML ben formato: jsoup ripulisce l'HTML e lo converte in DOM W3C
        org.w3c.dom.Document document = new W3CDom().fromJsoup(Jsoup.parse(html));

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withW3cDocument(document, null);
            if (WINDOWS_FONT.exists()) {
                builder.useFont(WINDOWS_FONT, "Calibri", 400, BaseRendererBuilder.FontStyle.NORMAL, true);
            }
            if (WINDOWS_FONT_BOLD.exists()) {
                builder.useFont(WINDOWS_FONT_BOLD, "Calibri", 700, BaseRendererBuilder.FontStyle.NORMAL, true);
            }
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new JobPilotException(HttpStatus.INTERNAL_SERVER_ERROR, "Errore nella creazione del PDF: " + e.getMessage(), e);
        }
    }

    /** Scrive i byte su disco creando le cartelle mancanti e restituisce il percorso assoluto. */
    public Path write(Path target, byte[] pdf) {
        try {
            Files.createDirectories(target.getParent());
            Files.write(target, pdf);
            log.info("PDF scritto: {}", target);
            return target.toAbsolutePath();
        } catch (IOException e) {
            throw new JobPilotException(HttpStatus.INTERNAL_SERVER_ERROR, "Non riesco a salvare il PDF in " + target + ": " + e.getMessage(), e);
        }
    }
}
