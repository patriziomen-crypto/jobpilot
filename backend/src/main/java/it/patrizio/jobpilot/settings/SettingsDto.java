package it.patrizio.jobpilot.settings;

import it.patrizio.jobpilot.ai.AiProviderConfig;
import it.patrizio.jobpilot.ai.AiProviderType;

import java.util.List;
import java.util.Objects;

/**
 * Impostazioni come le vede il frontend. Le chiavi non vengono mai rimandate indietro: solo
 * "presente sì/no" e gli ultimi 4 caratteri. In aggiornamento, apiKey vuota = lascia com'è.
 */
public record SettingsDto(
        List<ProviderDto> providers,
        List<AiProviderConfig.Preset> presets,
        String settingsFile
) {
    public record ProviderDto(
            String id,
            String name,
            AiProviderType type,
            String baseUrl,
            String apiKey,
            boolean apiKeyPresent,
            String apiKeyHint,
            String model,
            boolean enabled,
            boolean jsonMode,
            String lastStatus,
            String lastMessage,
            String lastCheckedAt
    ) {
        public static ProviderDto from(AiProviderConfig c) {
            String key = c.apiKey();
            String hint = key == null ? null : (key.length() > 16 ? "…" + key.substring(key.length() - 4) : "••••");
            return new ProviderDto(c.id(), c.name(), c.type(), c.baseUrl(), null, c.hasApiKey(), hint, c.model(), c.enabled(),
                    c.jsonMode(), c.lastStatus(), c.lastMessage(), c.lastCheckedAt());
        }

        /**
         * Ricostruisce la configurazione. La chiave già salvata viene mantenuta solo se non è stata
         * reinserita E l'indirizzo del servizio non è cambiato: altrimenti una pagina esterna potrebbe
         * dirottare la chiave verso un altro server cambiando solo baseUrl.
         * Lo stato (ultimo esito) si azzera se cambiano chiave, indirizzo o modello.
         */
        public AiProviderConfig toConfig(AiProviderConfig existing) {
            String key;
            boolean sameUrl = existing != null && Objects.equals(normalize(existing.baseUrl()), normalize(baseUrl)) && existing.type() == type;
            if (apiKey != null && !apiKey.isBlank()) {
                key = apiKey.strip();
            } else if (sameUrl) {
                key = existing.apiKey();
            } else {
                key = null;
            }
            boolean unchanged = existing != null && sameUrl && (apiKey == null || apiKey.isBlank())
                    && Objects.equals(existing.model(), model);
            return new AiProviderConfig(id, name, type, baseUrl, key, model, enabled, jsonMode,
                    unchanged ? existing.lastStatus() : null,
                    unchanged ? existing.lastMessage() : null,
                    unchanged ? existing.lastCheckedAt() : null);
        }

        private static String normalize(String url) {
            return url == null ? "" : url.strip().replaceAll("/+$", "").toLowerCase();
        }
    }
}
