package it.patrizio.jobpilot.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS: il frontend Angular in sviluppo (localhost:4200) può usare tutte le API; le pagine di
 * LinkedIn e Indeed (per il bookmarklet "Salva in JobPilot") possono chiamare SOLO l'import,
 * così un eventuale script malevolo su quei siti non raggiunge profilo, impostazioni e chiavi.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/applications/import")
                .allowedOriginPatterns(
                        "http://localhost:4200",
                        "http://127.0.0.1:4200",
                        "https://*.linkedin.com",
                        "https://*.indeed.com",
                        "https://*.indeed.it")
                .allowedMethods("POST", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Requested-With", "X-Profile-Id");

        registry.addMapping("/api/**")
                .allowedOriginPatterns("http://localhost:4200", "http://127.0.0.1:4200")
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Content-Type", "X-Requested-With", "X-Profile-Id");
    }
}
