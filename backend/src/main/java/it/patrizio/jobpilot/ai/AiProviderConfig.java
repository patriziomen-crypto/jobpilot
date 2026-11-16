package it.patrizio.jobpilot.ai;

import java.util.List;
import java.util.UUID;

/**
 * Un fornitore AI configurato dall'utente. L'ordine nella lista è l'ordine di prova:
 * se il primo fallisce (chiave scaduta, credito finito, servizio giù) si passa al successivo.
 * lastStatus/lastMessage/lastCheckedAt registrano l'esito dell'ultimo uso o dell'ultima prova.
 */
public record AiProviderConfig(
        String id,
        String name,
        AiProviderType type,
        String baseUrl,
        String apiKey,
        String model,
        boolean enabled,
        /** Chiede al servizio una risposta JSON garantita (response_format): supportato dalla maggior parte dei fornitori. */
        boolean jsonMode,
        /** "OK", "ERRORE" oppure null se mai usato. */
        String lastStatus,
        String lastMessage,
        String lastCheckedAt
) {
    public AiProviderConfig withApiKey(String newKey) {
        return new AiProviderConfig(id, name, type, baseUrl, newKey, model, enabled, jsonMode, lastStatus, lastMessage, lastCheckedAt);
    }

    public AiProviderConfig withId(String newId) {
        return new AiProviderConfig(newId, name, type, baseUrl, apiKey, model, enabled, jsonMode, lastStatus, lastMessage, lastCheckedAt);
    }

    public AiProviderConfig withStatus(String status, String message, String checkedAt) {
        return new AiProviderConfig(id, name, type, baseUrl, apiKey, model, enabled, jsonMode, status, message, checkedAt);
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }

    /** Mai la chiave nei log. */
    @Override
    public String toString() {
        return "AiProviderConfig[" + name + ", " + type + ", " + baseUrl + ", model=" + model
                + ", apiKey=" + (hasApiKey() ? "***" : "assente") + ", enabled=" + enabled + ", stato=" + lastStatus + "]";
    }

    /**
     * Controlla l'indirizzo di un servizio compatibile OpenAI: solo https, oppure http verso il proprio PC
     * (Ollama, LM Studio). Niente credenziali nell'URL. Restituisce il messaggio d'errore, o null se va bene.
     */
    public static String validateBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) return "manca l'indirizzo del servizio";
        java.net.URI uri;
        try {
            uri = new java.net.URI(baseUrl.strip());
        } catch (java.net.URISyntaxException e) {
            return "indirizzo del servizio non valido";
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase();
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase();
        if (uri.getUserInfo() != null) return "l'indirizzo non può contenere credenziali";
        if (host.isBlank()) return "indirizzo del servizio senza host";
        boolean local = host.equals("localhost") || host.equals("127.0.0.1") || host.equals("[::1]") || host.equals("::1");
        if (scheme.equals("https")) return null;
        if (scheme.equals("http") && local) return null;
        return "l'indirizzo deve usare https (http è ammesso solo verso localhost)";
    }

    /** Valori predefiniti per i servizi più comuni: l'utente deve aggiungere solo la chiave. */
    public record Preset(String key, String name, AiProviderType type, String baseUrl, String model, boolean jsonMode,
                         boolean needsKey, String note) {}

    public static final List<Preset> PRESETS = List.of(
            new Preset("claude", "Claude (Anthropic)", AiProviderType.CLAUDE, null, "claude-opus-5-5", true, true,
                    "Chiave da console.anthropic.com (a pagamento). Qualità migliore per CV e lettere."),
            new Preset("groq", "Groq", AiProviderType.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", "openai/gpt-oss-120b", true, true,
                    "Gratis con limiti giornalieri, chiave da console.groq.com. I nomi dei modelli cambiano spesso: usa l'elenco modelli."),
            new Preset("gemini", "Google Gemini", AiProviderType.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", true, true,
                    "Gratis con limiti, chiave da aistudio.google.com."),
            new Preset("openai", "OpenAI (ChatGPT)", AiProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-5-mini", true, true,
                    "A pagamento, chiave da platform.openai.com."),
            new Preset("github", "GitHub Models", AiProviderType.OPENAI_COMPATIBLE, "https://models.github.ai/inference", "openai/gpt-4.1", true, true,
                    "Incluso nei piani GitHub con limiti giornalieri. Usa un token GitHub (Settings → Developer settings → Personal access tokens, permesso \"models\")."),
            new Preset("mistral", "Mistral", AiProviderType.OPENAI_COMPATIBLE, "https://api.mistral.ai/v1", "mistral-small-latest", true, true,
                    "Chiave da console.mistral.ai."),
            new Preset("openrouter", "OpenRouter", AiProviderType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", true, true,
                    "Un'unica chiave per molti modelli diversi (openrouter.ai)."),
            new Preset("ollama", "Ollama (locale, gratis)", AiProviderType.OPENAI_COMPATIBLE, "http://localhost:11434/v1", "llama3.1", true, false,
                    "Gira sul tuo PC senza abbonamenti: installa Ollama da ollama.com e scarica un modello (es. \"ollama pull llama3.1\")."),
            new Preset("custom", "Altro (compatibile OpenAI)", AiProviderType.OPENAI_COMPATIBLE, "", "", true, true,
                    "Qualsiasi servizio con endpoint /chat/completions.")
    );

    public static AiProviderConfig fromPreset(Preset preset) {
        return new AiProviderConfig(UUID.randomUUID().toString(), preset.name(), preset.type(), preset.baseUrl(), null,
                preset.model(), true, preset.jsonMode(), null, null, null);
    }
}
