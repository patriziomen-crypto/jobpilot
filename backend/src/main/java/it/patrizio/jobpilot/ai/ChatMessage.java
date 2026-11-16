package it.patrizio.jobpilot.ai;

/** Un messaggio della conversazione con l'assistente: role = "user" oppure "assistant". */
public record ChatMessage(String role, String content) {

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content);
    }
}
