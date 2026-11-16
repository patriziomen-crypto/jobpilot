package it.patrizio.jobpilot.ai;

import it.patrizio.jobpilot.jobs.JobOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Qualsiasi servizio con API "chat completions" compatibile OpenAI: OpenAI, GitHub Models, Gemini,
 * Groq, Mistral, OpenRouter, Ollama in locale, ecc. La risposta è chiesta in JSON e convertita nei record.
 */
public class OpenAiCompatibleProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProvider.class);

    private final AiProviderConfig config;
    private final ObjectMapper mapper;
    private final RestClient http;

    public OpenAiCompatibleProvider(AiProviderConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        String problem = AiProviderConfig.validateBaseUrl(config.baseUrl());
        if (problem != null) {
            throw new AiException(AiException.Kind.AUTH, config.name() + ": " + problem);
        }
        // Timeout espliciti: senza, un server che accetta la connessione e non risponde bloccherebbe l'analisi per sempre
        org.springframework.http.client.JdkClientHttpRequestFactory factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(10)).build());
        factory.setReadTimeout(java.time.Duration.ofSeconds(120));
        RestClient.Builder builder = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(config.baseUrl().replaceAll("/+$", ""));
        if (config.hasApiKey()) {
            builder.defaultHeader("Authorization", "Bearer " + config.apiKey());
        }
        this.http = builder.build();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String model() {
        return config.model();
    }

    @Override
    public JobAnalysisResult analyze(String profileText, JobOffer offer) {
        return call(Prompts.analyzeSystem(profileText) + "\n\n" + Prompts.ANALYSIS_JSON, Prompts.analyzeUser(offer), JobAnalysisResult.class);
    }

    @Override
    public TailoredCvResult tailorCv(String profileText, JobOffer offer, String keywords) {
        return call(Prompts.cvSystem(profileText) + "\n\n" + Prompts.CV_JSON, Prompts.cvUser(offer, keywords), TailoredCvResult.class);
    }

    @Override
    public CoverLetterResult coverLetter(String profileText, JobOffer offer, String analysisSummary) {
        return call(Prompts.letterSystem(profileText) + "\n\n" + Prompts.LETTER_JSON, Prompts.letterUser(offer, analysisSummary), CoverLetterResult.class);
    }

    @Override
    public String ping() {
        String text = chat("Rispondi solo con la parola: OK", "Ciao", false);
        return "Connessione riuscita con " + model() + " (risposta: " + text.strip() + ")";
    }

    @Override
    public String shortAnswer(String profileText, String question, List<String> options) {
        String text = chat(Prompts.shortAnswerSystem(profileText), Prompts.shortAnswerUser(question, options), false).strip();
        return text.isBlank() || text.toUpperCase().startsWith("NON SO") ? null : text.replaceAll("^[\"'`]+|[\"'`.]+$", "");
    }

    /** GET /models: elenco dei modelli di testo disponibili (esclusi audio, immagini, moderazione). */
    @Override
    public List<String> listModels() {
        try {
            String raw = http.get().uri("/models").retrieve().body(String.class);
            JsonNode data = mapper.readTree(raw == null ? "{}" : raw).path("data");
            List<String> ids = new ArrayList<>();
            for (JsonNode m : data) {
                String id = m.path("id").asString("");
                if (!id.isBlank() && !id.matches("(?i).*(whisper|tts|guard|vision|audio|embed|image|dall-e|moderation|realtime|transcribe|orpheus).*")) {
                    ids.add(id);
                }
            }
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            if (code == 401 || code == 403) throw new AiException(AiException.Kind.AUTH, config.name() + ": chiave non valida (" + code + ")", e);
            throw new AiException(AiException.Kind.BAD_RESPONSE, config.name() + ": elenco modelli non disponibile (" + code + ")", e);
        } catch (ResourceAccessException e) {
            throw new AiException(AiException.Kind.UNAVAILABLE, config.name() + ": non raggiungibile", e);
        } catch (RuntimeException e) {
            throw new AiException(AiException.Kind.BAD_RESPONSE, config.name() + ": elenco modelli non leggibile", e);
        }
    }

    private <T> T call(String system, String user, Class<T> type) {
        String content = chat(system, user, config.jsonMode());
        String json = extractJson(content);
        try {
            return mapper.readValue(json, type);
        } catch (RuntimeException e) {
            log.warn("{}: risposta non in JSON valido: {}", config.name(), content.length() > 300 ? content.substring(0, 300) + "…" : content);
            throw new AiException(AiException.Kind.BAD_RESPONSE, config.name() + " ha risposto in un formato non leggibile", e);
        }
    }

    @Override
    public ChatReply chat(String system, List<ChatMessage> history) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system + "\n\n" + Prompts.CHAT_JSON));
        for (ChatMessage m : history) {
            messages.add(Map.of("role", "assistant".equals(m.role()) ? "assistant" : "user", "content", m.content()));
        }
        String content = complete(messages, config.jsonMode());
        try {
            ChatReply reply = mapper.readValue(extractJson(content), ChatReply.class);
            if (reply.reply() != null && !reply.reply().isBlank()) return reply;
        } catch (RuntimeException e) {
            log.debug("{}: risposta chat non in JSON, la uso come testo", config.name());
        }
        // Il modello ha risposto in testo libero: va bene lo stesso, senza azioni
        return new ChatReply(content.strip(), List.of());
    }

    private String chat(String system, String user, boolean jsonMode) {
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", system));
        messages.add(Map.of("role", "user", "content", user));
        return complete(messages, jsonMode);
    }

    private String complete(List<Map<String, String>> messages, boolean jsonMode) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model());
        body.put("messages", messages);
        body.put("temperature", 0.3);
        if (jsonMode) {
            body.put("response_format", Map.of("type", "json_object"));
        }
        try {
            String raw = http.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            JsonNode root = mapper.readTree(raw == null ? "{}" : raw);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiException(AiException.Kind.BAD_RESPONSE, config.name() + ": risposta senza contenuto");
            }
            JsonNode message = choices.get(0).path("message");
            String content = message.path("content").asString("");
            if (content.isBlank() && "content_filter".equals(choices.get(0).path("finish_reason").asString(""))) {
                throw new AiException(AiException.Kind.REFUSED, config.name() + " ha rifiutato la richiesta");
            }
            return content;
        } catch (HttpClientErrorException e) {
            int code = e.getStatusCode().value();
            String detail = e.getResponseBodyAsString();
            if (code == 401 || code == 403) throw new AiException(AiException.Kind.AUTH, config.name() + ": chiave non valida o senza permessi (" + code + ")", e);
            if (code == 402 || (code == 429 && detail.toLowerCase().matches("(?s).*(quota|credit|billing|insufficient).*"))) {
                throw new AiException(AiException.Kind.QUOTA, config.name() + ": credito o quota esauriti", e);
            }
            if (code == 429) throw new AiException(AiException.Kind.RATE_LIMIT, config.name() + ": troppe richieste (429)", e);
            if (code == 404) throw new AiException(AiException.Kind.BAD_RESPONSE, config.name() + ": modello \"" + model() + "\" non trovato o indirizzo errato", e);
            if (code == 400 && jsonMode && detail.toLowerCase().contains("response_format")) {
                log.info("{}: response_format non supportato, riprovo senza", config.name());
                return complete(messages, false);
            }
            throw new AiException(AiException.Kind.BAD_RESPONSE, config.name() + ": errore " + code + " " + shorten(detail), e);
        } catch (HttpServerErrorException e) {
            throw new AiException(AiException.Kind.UNAVAILABLE, config.name() + ": servizio non disponibile (" + e.getStatusCode().value() + ")", e);
        } catch (ResourceAccessException e) {
            throw new AiException(AiException.Kind.UNAVAILABLE, config.name() + ": non raggiungibile (" + e.getMessage() + ")", e);
        }
    }

    /** Toglie eventuali ``` e testo attorno al JSON. */
    static String extractJson(String content) {
        if (content == null) return "{}";
        String s = content.strip();
        if (s.startsWith("```")) {
            s = s.replaceFirst("^```[a-zA-Z]*\\s*", "").replaceFirst("\\s*```$", "").strip();
        }
        int start = s.indexOf('{');
        int end = s.lastIndexOf('}');
        if (start >= 0 && end > start) {
            s = s.substring(start, end + 1);
        }
        return s;
    }

    private static String shorten(String s) {
        if (s == null) return "";
        String t = s.replaceAll("\\s+", " ").strip();
        return t.length() > 200 ? t.substring(0, 200) + "…" : t;
    }
}
