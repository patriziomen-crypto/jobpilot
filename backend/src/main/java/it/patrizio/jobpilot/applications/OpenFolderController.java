package it.patrizio.jobpilot.applications;

import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.config.JobPilotProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Apre in Esplora file la cartella con i PDF della candidatura (l'app gira sullo stesso PC del browser). */
@RestController
@RequestMapping("/api/applications")
public class OpenFolderController {

    private final ApplicationService applications;
    private final JobPilotProperties properties;

    public OpenFolderController(ApplicationService applications, JobPilotProperties properties) {
        this.applications = applications;
        this.properties = properties;
    }

    @PostMapping("/{id}/open-folder")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void openFolder(@PathVariable Long id) {
        Application app = applications.findForCurrentProfile(id);
        String anyPath = app.getCvPath() != null ? app.getCvPath() : app.getLetterPath();
        if (anyPath == null) {
            throw JobPilotException.badRequest("Nessun documento ancora generato per questa candidatura");
        }
        Path folder = Path.of(anyPath).getParent().toAbsolutePath().normalize();
        Path outputRoot = properties.outputDir().toAbsolutePath().normalize();
        // Per sicurezza apriamo solo cartelle dentro la cartella di output dell'app
        if (!folder.startsWith(outputRoot) || !Files.isDirectory(folder)) {
            throw JobPilotException.badRequest("La cartella dei documenti non esiste più: rigenera CV e lettera");
        }
        try {
            new ProcessBuilder("explorer.exe", folder.toString()).start();
        } catch (IOException e) {
            throw new JobPilotException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile aprire Esplora file", e);
        }
    }
}
