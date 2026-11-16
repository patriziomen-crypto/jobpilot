package it.patrizio.jobpilot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;

/** Impostazioni dell'app lette dal blocco "jobpilot" di application.yml. */
@ConfigurationProperties(prefix = "jobpilot")
public record JobPilotProperties(Path outputDir, Ai ai) {

    public record Ai(String model, String apiKey) {
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
