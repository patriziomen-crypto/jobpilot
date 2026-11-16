package it.patrizio.jobpilot.profile;

import it.patrizio.jobpilot.config.JobPilotException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Optional;

/**
 * La persona "attiva" nella richiesta corrente: il frontend la indica con l'header X-Profile-Id.
 * Senza header (o fuori da una richiesta web) si usa il primo profilo. Un header presente ma
 * non valido è un errore: meglio fermarsi che scrivere i dati sulla persona sbagliata.
 */
@Component
public class ProfileContext {

    public static final String HEADER = "X-Profile-Id";

    private final ProfileRepository profiles;

    public ProfileContext(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    private Optional<String> requestedHeader() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            String header = request.getHeader(HEADER);
            if (header != null && !header.isBlank()) {
                return Optional.of(header.strip());
            }
        }
        return Optional.empty();
    }

    /** Id del profilo attivo: dall'header se presente e valido, altrimenti il primo in archivio. */
    public Long currentProfileId() {
        Optional<String> header = requestedHeader();
        if (header.isPresent()) {
            long id;
            try {
                id = Long.parseLong(header.get());
            } catch (NumberFormatException e) {
                throw JobPilotException.badRequest("Identificativo della persona non valido");
            }
            if (!profiles.existsById(id)) {
                throw JobPilotException.notFound("Persona non trovata: seleziona un'altra persona dal menu in alto");
            }
            return id;
        }
        return profiles.findFirstByOrderByIdAsc().map(Profile::getId)
                .orElseThrow(() -> new IllegalStateException("Nessun profilo in archivio: eseguire le migrazioni"));
    }
}
