package it.patrizio.jobpilot.settings;

import it.patrizio.jobpilot.ai.AiProviderConfig;
import it.patrizio.jobpilot.ai.AiRouter;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/settings")
public class SettingsController {

    private final SettingsService settings;
    private final AiRouter router;

    public SettingsController(SettingsService settings, AiRouter router) {
        this.settings = settings;
        this.router = router;
    }

    @GetMapping
    public SettingsDto get() {
        return toDto();
    }

    /** Sostituisce l'elenco dei fornitori (ordine = priorità). Le chiavi non reinserite restano quelle salvate. */
    @PutMapping
    public SettingsDto update(@RequestBody SettingsDto dto) {
        List<AiProviderConfig> existing = settings.aiProviders();
        List<AiProviderConfig> updated = new ArrayList<>();
        for (SettingsDto.ProviderDto p : dto.providers()) {
            AiProviderConfig old = existing.stream().filter(c -> c.id() != null && c.id().equals(p.id())).findFirst().orElse(null);
            updated.add(p.toConfig(old));
        }
        settings.saveAiProviders(updated);
        return toDto();
    }

    /** Fa una chiamata minima al fornitore per verificare chiave e connessione. */
    @PostMapping("/providers/{id}/test")
    public Map<String, String> test(@PathVariable String id) {
        return Map.of("message", router.ping(id));
    }

    /** Elenco dei modelli che il fornitore offre con la chiave salvata. */
    @GetMapping("/providers/{id}/models")
    public List<String> models(@PathVariable String id) {
        return router.listModels(id);
    }

    private SettingsDto toDto() {
        return new SettingsDto(
                settings.aiProviders().stream().map(SettingsDto.ProviderDto::from).toList(),
                AiProviderConfig.PRESETS,
                settings.file().toString());
    }
}
