package it.patrizio.jobpilot.ai;

import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.jobs.JobOffer;
import it.patrizio.jobpilot.profile.Profile;
import it.patrizio.jobpilot.settings.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Sceglie il fornitore AI: prova quelli attivi nell'ordine impostato dall'utente e, se tutti falliscono
 * (chiave scaduta, credito finito, servizio giù), usa la modalità base senza AI. Così l'app non si ferma mai.
 */
@Service
public class AiRouter {

    private static final Logger log = LoggerFactory.getLogger(AiRouter.class);

    private final SettingsService settings;
    private final ObjectMapper mapper;

    public AiRouter(SettingsService settings, ObjectMapper mapper) {
        this.settings = settings;
        this.mapper = mapper;
    }

    /** Risultato più il nome di chi l'ha prodotto. */
    public record AiResult<T>(T value, String provider, String model, List<String> warnings) {}

    public AiResult<JobAnalysisResult> analyze(Profile profile, String profileText, JobOffer offer) {
        return run(profile, p -> p.analyze(profileText, offer), "analizzare l'offerta");
    }

    public AiResult<TailoredCvResult> tailorCv(Profile profile, String profileText, JobOffer offer, String keywords) {
        return run(profile, p -> p.tailorCv(profileText, offer, keywords), "generare il CV");
    }

    public AiResult<CoverLetterResult> coverLetter(Profile profile, String profileText, JobOffer offer, String summary) {
        return run(profile, p -> p.coverLetter(profileText, offer, summary), "scrivere la lettera");
    }

    /** Verifica un singolo fornitore (pulsante "Prova" nelle impostazioni). */
    public String ping(String providerId) {
        AiProviderConfig config = settings.aiProviders().stream()
                .filter(c -> c.id().equals(providerId)).findFirst()
                .orElseThrow(() -> JobPilotException.notFound("Fornitore non trovato"));
        try {
            String result = build(config).ping();
            settings.updateProviderStatus(config.id(), true, result);
            return result;
        } catch (AiException e) {
            settings.updateProviderStatus(config.id(), false, e.getMessage());
            HttpStatus status = switch (e.getKind()) {
                case AUTH -> HttpStatus.UNAUTHORIZED;
                case QUOTA -> HttpStatus.PAYMENT_REQUIRED;
                case RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
                default -> HttpStatus.BAD_GATEWAY;
            };
            throw new JobPilotException(status, e.getMessage(), e);
        }
    }

    /** Risposta breve a una domanda di un modulo; null se nessun fornitore AI risponde (la modalità base non lo fa). */
    public String shortAnswer(Profile profile, String profileText, String question, List<String> options) {
        for (AiProviderConfig config : settings.aiProviders()) {
            if (!config.enabled() || config.type() == AiProviderType.NONE) continue;
            try {
                String answer = build(config).shortAnswer(profileText, question, options);
                if (answer != null) return answer;
            } catch (AiException e) {
                log.warn("{}: risposta al modulo non disponibile: {}", config.name(), e.getMessage());
            } catch (RuntimeException e) {
                log.warn("{}: errore nella risposta al modulo", config.name(), e);
            }
        }
        return null;
    }

    /**
     * Conversazione con l'assistente: prova i fornitori attivi in ordine. Se nessuno risponde lancia
     * AiException (la modalità base non sa chattare: ci pensa il chiamante con una risposta fissa).
     */
    public AiResult<ChatReply> chat(String system, List<ChatMessage> history) {
        List<String> warnings = new ArrayList<>();
        for (AiProviderConfig config : settings.aiProviders()) {
            if (!config.enabled() || config.type() == AiProviderType.NONE) continue;
            try {
                AiProvider provider = build(config);
                ChatReply reply = provider.chat(system, history);
                settings.updateProviderStatus(config.id(), true, "Ultimo uso riuscito: assistente");
                return new AiResult<>(reply, provider.name(), provider.model(), warnings);
            } catch (AiException e) {
                log.warn("{}: assistente non disponibile ({}): {}", config.name(), e.getKind(), e.getMessage());
                warnings.add(config.name() + ": " + e.getMessage());
                settings.updateProviderStatus(config.id(), false, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("{}: errore inatteso nell'assistente", config.name(), e);
                warnings.add(config.name() + ": " + e.getMessage());
                settings.updateProviderStatus(config.id(), false, "Errore inatteso: " + e.getMessage());
            }
        }
        throw new AiException(AiException.Kind.UNAVAILABLE,
                warnings.isEmpty() ? "Nessun fornitore AI attivo" : String.join("; ", warnings));
    }

    /** Modelli disponibili presso un fornitore (pulsante "Modelli disponibili" nelle impostazioni). */
    public List<String> listModels(String providerId) {
        AiProviderConfig config = settings.aiProviders().stream()
                .filter(c -> c.id().equals(providerId)).findFirst()
                .orElseThrow(() -> JobPilotException.notFound("Fornitore non trovato"));
        try {
            return build(config).listModels();
        } catch (AiException e) {
            HttpStatus status = e.getKind() == AiException.Kind.AUTH ? HttpStatus.UNAUTHORIZED : HttpStatus.BAD_GATEWAY;
            throw new JobPilotException(status, e.getMessage(), e);
        }
    }

    /** True se c'è almeno un fornitore AI vero attivo (oltre alla modalità base). */
    public boolean hasRealProvider() {
        return settings.aiProviders().stream().anyMatch(c -> c.enabled() && c.type() != AiProviderType.NONE);
    }

    private <T> AiResult<T> run(Profile profile, Function<AiProvider, T> action, String azione) {
        List<String> warnings = new ArrayList<>();
        for (AiProviderConfig config : settings.aiProviders()) {
            if (!config.enabled() || config.type() == AiProviderType.NONE) continue;
            AiProvider provider;
            try {
                provider = build(config);
            } catch (AiException e) {
                warnings.add(config.name() + ": " + e.getMessage());
                settings.updateProviderStatus(config.id(), false, e.getMessage());
                continue;
            }
            try {
                T value = action.apply(provider);
                if (!warnings.isEmpty()) {
                    log.info("{} riuscito con {} dopo: {}", azione, provider.name(), warnings);
                }
                settings.updateProviderStatus(config.id(), true, "Ultimo uso riuscito: " + azione);
                return new AiResult<>(value, provider.name(), provider.model(), warnings);
            } catch (AiException e) {
                log.warn("{}: impossibile {} ({}): {}", provider.name(), azione, e.getKind(), e.getMessage());
                warnings.add(provider.name() + ": " + e.getMessage());
                settings.updateProviderStatus(config.id(), false, e.getMessage());
            } catch (RuntimeException e) {
                log.warn("{}: errore inatteso nel {}", provider.name(), azione, e);
                warnings.add(provider.name() + ": " + e.getMessage());
                settings.updateProviderStatus(config.id(), false, "Errore inatteso: " + e.getMessage());
            }
        }
        // Nessun fornitore AI disponibile: modalità base
        TemplateProvider base = new TemplateProvider(profile);
        T value = action.apply(base);
        return new AiResult<>(value, base.name(), base.model(), warnings);
    }

    private AiProvider build(AiProviderConfig config) {
        return switch (config.type()) {
            case CLAUDE -> new ClaudeProvider(config);
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleProvider(config, mapper);
            case NONE -> throw new AiException(AiException.Kind.UNAVAILABLE, "La modalità base non va testata");
        };
    }
}
