package it.patrizio.jobpilot.settings;

import it.patrizio.jobpilot.ai.AiProviderConfig;
import it.patrizio.jobpilot.ai.AiProviderType;
import it.patrizio.jobpilot.config.JobPilotException;
import it.patrizio.jobpilot.config.JobPilotProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

/**
 * Impostazioni modificabili dall'interfaccia (fornitori AI con le loro chiavi).
 * Vengono salvate in %LOCALAPPDATA%\JobPilot\jobpilot.properties, così restano anche
 * quando si ricompila l'app. Hanno la precedenza su application.yml e variabili d'ambiente.
 */
@Service
public class SettingsService {

    private static final Logger log = LoggerFactory.getLogger(SettingsService.class);

    static final String AI_PROVIDERS = "ai.providers";
    static final String LEGACY_CLAUDE_KEY = "claude.api-key";
    static final String LEGACY_CLAUDE_MODEL = "claude.model";

    private final Path file;
    private final JobPilotProperties properties;
    private final ObjectMapper mapper;
    private final Properties values = new Properties();
    private volatile List<AiProviderConfig> providersCache;

    public SettingsService(JobPilotProperties properties, ObjectMapper mapper) {
        this.properties = properties;
        this.mapper = mapper;
        String localAppData = System.getenv("LOCALAPPDATA");
        Path base = localAppData != null ? Path.of(localAppData) : Path.of(System.getProperty("user.home"), "AppData", "Local");
        this.file = base.resolve("JobPilot").resolve("jobpilot.properties");
        load();
        migrateLegacyClaudeKey();
    }

    private synchronized void load() {
        values.clear();
        if (Files.exists(file)) {
            try (InputStream in = Files.newInputStream(file)) {
                values.load(in);
                log.info("Impostazioni caricate da {}", file);
            } catch (IOException e) {
                log.warn("Impossibile leggere {}: {}", file, e.getMessage());
            }
        }
        providersCache = null;
    }

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                values.store(out, "Impostazioni di JobPilot");
            }
            restrictToCurrentUser(file);
        } catch (IOException e) {
            throw new JobPilotException(HttpStatus.INTERNAL_SERVER_ERROR, "Impossibile salvare le impostazioni in " + file + ": " + e.getMessage(), e);
        }
    }

    public synchronized String get(String key) {
        String v = values.getProperty(key);
        return v == null || v.isBlank() ? null : v.strip();
    }

    public synchronized void set(String key, String value) {
        if (value == null || value.isBlank()) {
            values.remove(key);
        } else {
            values.setProperty(key, value.strip());
        }
    }

    public boolean getBoolean(String key) {
        return "true".equalsIgnoreCase(get(key));
    }

    public int getInt(String key, int fallback) {
        try {
            String v = get(key);
            return v == null ? fallback : Integer.parseInt(v);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Fornitori AI nell'ordine di prova. La lista può essere vuota: resta la modalità base. */
    public List<AiProviderConfig> aiProviders() {
        List<AiProviderConfig> cached = providersCache;
        if (cached != null) return cached;
        synchronized (this) {
            String json = get(AI_PROVIDERS);
            List<AiProviderConfig> list = new ArrayList<>();
            if (json != null) {
                try {
                    AiProviderConfig[] arr = mapper.readValue(json, AiProviderConfig[].class);
                    for (AiProviderConfig c : arr) list.add(c);
                } catch (RuntimeException e) {
                    log.warn("Elenco fornitori AI non leggibile, lo ignoro: {}", e.getMessage());
                }
            }
            providersCache = List.copyOf(list);
            return providersCache;
        }
    }

    public synchronized void saveAiProviders(List<AiProviderConfig> providers) {
        List<AiProviderConfig> clean = new ArrayList<>();
        for (AiProviderConfig c : providers) {
            if (c.type() == AiProviderType.OPENAI_COMPATIBLE) {
                String problem = AiProviderConfig.validateBaseUrl(c.baseUrl());
                if (problem != null) {
                    throw JobPilotException.badRequest(c.name() + ": " + problem);
                }
            }
            clean.add(c.id() == null || c.id().isBlank() ? c.withId(UUID.randomUUID().toString()) : c);
        }
        set(AI_PROVIDERS, mapper.writeValueAsString(clean));
        providersCache = List.copyOf(clean);
        save();
    }

    /** Registra l'esito dell'ultimo uso o dell'ultima prova di un fornitore (mostrato come stato nelle impostazioni). */
    public synchronized void updateProviderStatus(String providerId, boolean ok, String message) {
        List<AiProviderConfig> updated = new ArrayList<>();
        boolean changed = false;
        for (AiProviderConfig c : aiProviders()) {
            if (c.id() != null && c.id().equals(providerId)) {
                String msg = message == null ? null : (message.length() > 300 ? message.substring(0, 300) + "…" : message);
                updated.add(c.withStatus(ok ? "OK" : "ERRORE", msg, java.time.LocalDateTime.now().withNano(0).toString()));
                changed = true;
            } else {
                updated.add(c);
            }
        }
        if (changed) {
            set(AI_PROVIDERS, mapper.writeValueAsString(updated));
            providersCache = List.copyOf(updated);
            save();
        }
    }

    /** Il file delle impostazioni contiene chiavi: lo rendiamo leggibile solo dall'utente corrente (Windows ACL). */
    private void restrictToCurrentUser(Path target) {
        try {
            java.nio.file.attribute.AclFileAttributeView view =
                    Files.getFileAttributeView(target, java.nio.file.attribute.AclFileAttributeView.class);
            if (view == null) return;
            java.nio.file.attribute.UserPrincipal owner = Files.getOwner(target);
            java.nio.file.attribute.AclEntry entry = java.nio.file.attribute.AclEntry.newBuilder()
                    .setType(java.nio.file.attribute.AclEntryType.ALLOW)
                    .setPrincipal(owner)
                    .setPermissions(java.util.EnumSet.allOf(java.nio.file.attribute.AclEntryPermission.class))
                    .build();
            view.setAcl(List.of(entry));
        } catch (Exception e) {
            log.debug("Permessi del file impostazioni non ristretti: {}", e.getMessage());
        }
    }

    /** Converte la vecchia impostazione "chiave Claude" (o application.yml / variabile d'ambiente) in un fornitore. */
    private void migrateLegacyClaudeKey() {
        if (!aiProviders().isEmpty()) return;
        String key = get(LEGACY_CLAUDE_KEY);
        if (key == null && properties.ai().hasApiKey()) key = properties.ai().apiKey();
        if (key == null) {
            String env = System.getenv("ANTHROPIC_API_KEY");
            if (env != null && !env.isBlank()) key = env.strip();
        }
        if (key == null) return;
        String model = get(LEGACY_CLAUDE_MODEL) != null ? get(LEGACY_CLAUDE_MODEL) : properties.ai().model();
        AiProviderConfig claude = new AiProviderConfig(UUID.randomUUID().toString(), "Claude (Anthropic)",
                AiProviderType.CLAUDE, null, key, model, true, true, null, null, null);
        saveAiProviders(List.of(claude));
        set(LEGACY_CLAUDE_KEY, null);
        set(LEGACY_CLAUDE_MODEL, null);
        save();
        log.info("Chiave Claude esistente convertita in fornitore AI");
    }

    public Path file() {
        return file;
    }
}
