package it.patrizio.jobpilot.ai;

/** Famiglie di fornitori AI supportate. */
public enum AiProviderType {
    /** Anthropic Claude, tramite SDK ufficiale. */
    CLAUDE,
    /** Qualsiasi servizio con API "chat completions" compatibile OpenAI: OpenAI, GitHub Models, Gemini, Groq, Mistral, OpenRouter, Ollama... */
    OPENAI_COMPATIBLE,
    /** Modalità base senza AI: regole e modelli di testo. Sempre disponibile. */
    NONE
}
