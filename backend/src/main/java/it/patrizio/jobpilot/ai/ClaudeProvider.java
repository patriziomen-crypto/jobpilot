package it.patrizio.jobpilot.ai;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.CacheControlEphemeral;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import it.patrizio.jobpilot.jobs.JobOffer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/** Claude di Anthropic tramite SDK ufficiale, con risposte strutturate native. */
public class ClaudeProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(ClaudeProvider.class);

    private final AiProviderConfig config;
    private final AnthropicClient client;

    public ClaudeProvider(AiProviderConfig config) {
        this.config = config;
        if (!config.hasApiKey()) {
            throw new AiException(AiException.Kind.AUTH, config.name() + ": chiave mancante");
        }
        this.client = AnthropicOkHttpClient.builder().apiKey(config.apiKey()).build();
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public String model() {
        return config.model() == null || config.model().isBlank() ? "claude-opus-5-5" : config.model();
    }

    @Override
    public JobAnalysisResult analyze(String profileText, JobOffer offer) {
        return call(Prompts.analyzeSystem(profileText), Prompts.analyzeUser(offer), JobAnalysisResult.class, 4096L);
    }

    @Override
    public TailoredCvResult tailorCv(String profileText, JobOffer offer, String keywords) {
        return call(Prompts.cvSystem(profileText), Prompts.cvUser(offer, keywords), TailoredCvResult.class, 8192L);
    }

    @Override
    public CoverLetterResult coverLetter(String profileText, JobOffer offer, String analysisSummary) {
        return call(Prompts.letterSystem(profileText), Prompts.letterUser(offer, analysisSummary), CoverLetterResult.class, 4096L);
    }

    @Override
    public String ping() {
        try {
            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(model())
                    .maxTokens(64L)
                    .addUserMessage("Rispondi solo con la parola: OK")
                    .build());
            String text = response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst().orElse("");
            return "Connessione riuscita con " + model() + " (risposta: " + text.strip() + ")";
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public String shortAnswer(String profileText, String question, List<String> options) {
        try {
            Message response = client.messages().create(MessageCreateParams.builder()
                    .model(model())
                    .maxTokens(200L)
                    .system(Prompts.shortAnswerSystem(profileText))
                    .addUserMessage(Prompts.shortAnswerUser(question, options))
                    .build());
            String text = response.content().stream().flatMap(b -> b.text().stream()).map(t -> t.text()).findFirst().orElse("").strip();
            return text.isBlank() || text.toUpperCase().startsWith("NON SO") ? null : text.replaceAll("^[\"'`]+|[\"'`.]+$", "");
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public List<String> listModels() {
        try {
            List<String> ids = new ArrayList<>();
            client.models().list().autoPager().forEach(m -> ids.add(m.id()));
            ids.sort(String.CASE_INSENSITIVE_ORDER);
            return ids;
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    @Override
    public ChatReply chat(String system, List<ChatMessage> history) {
        MessageCreateParams.Builder builder = MessageCreateParams.builder()
                .model(model())
                .maxTokens(2048L)
                .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(system)
                        .cacheControl(CacheControlEphemeral.builder().build()).build()));
        for (ChatMessage m : history) {
            if ("assistant".equals(m.role())) builder.addAssistantMessage(m.content());
            else builder.addUserMessage(m.content());
        }
        return execute(builder.outputConfig(ChatReply.class).build());
    }

    private <T> T call(String system, String user, Class<T> type, long maxTokens) {
        StructuredMessageCreateParams<T> params = MessageCreateParams.builder()
                .model(model())
                .maxTokens(maxTokens)
                // Il profilo cambia di rado: con cache_control le chiamate successive lo rileggono dalla cache
                .systemOfTextBlockParams(List.of(TextBlockParam.builder().text(system)
                        .cacheControl(CacheControlEphemeral.builder().build()).build()))
                .outputConfig(type)
                .addUserMessage(user)
                .build();
        return execute(params);
    }

    private <T> T execute(StructuredMessageCreateParams<T> params) {
        try {
            StructuredMessage<T> response = client.messages().create(params);
            log.debug("Claude: stop={} input={} cacheRead={} output={}", response.stopReason(),
                    response.usage().inputTokens(), response.usage().cacheReadInputTokens(), response.usage().outputTokens());
            boolean refused = response.stopReason().map(r -> r.toString().equalsIgnoreCase("refusal")).orElse(false);
            if (refused) {
                throw new AiException(AiException.Kind.REFUSED, "Claude ha rifiutato la richiesta per questo annuncio");
            }
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(typed -> typed.text())
                    .findFirst()
                    .orElseThrow(() -> new AiException(AiException.Kind.BAD_RESPONSE, "Risposta di Claude senza contenuto"));
        } catch (AiException e) {
            throw e;
        } catch (RuntimeException e) {
            throw translate(e);
        }
    }

    private AiException translate(RuntimeException e) {
        if (e instanceof RateLimitException) {
            return new AiException(AiException.Kind.RATE_LIMIT, "Claude è sovraccarico (429)", e);
        }
        if (e instanceof AnthropicServiceException se) {
            int code = se.statusCode();
            if (code == 401 || code == 403) return new AiException(AiException.Kind.AUTH, "Chiave Claude non valida (" + code + ")", e);
            if (code == 402 || (code == 400 && String.valueOf(se.getMessage()).toLowerCase().contains("credit"))) {
                return new AiException(AiException.Kind.QUOTA, "Credito Claude esaurito", e);
            }
            if (code >= 500 || code == 529) return new AiException(AiException.Kind.UNAVAILABLE, "Claude non disponibile (" + code + ")", e);
            return new AiException(AiException.Kind.BAD_RESPONSE, "Errore da Claude (" + code + "): " + se.getMessage(), e);
        }
        if (e instanceof AnthropicIoException) {
            return new AiException(AiException.Kind.UNAVAILABLE, "Impossibile raggiungere Claude: controlla la connessione", e);
        }
        return new AiException(AiException.Kind.BAD_RESPONSE, "Errore Claude: " + e.getMessage(), e);
    }
}
