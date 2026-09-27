package com.geekyants.assessment.llm;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredOutputConfig;
import com.anthropic.models.messages.ThinkingConfigAdaptive;
import com.geekyants.assessment.config.AppProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Claude implementation using structured outputs, so every response is parsed into a typed record
 * instead of free text. Reads ANTHROPIC_API_KEY (or another SDK credential source) from the environment.
 */
@Component
public class ClaudeLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeLlmClient.class);
    private static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

    private final AppProperties.Llm config;
    private volatile AnthropicClient client;

    public ClaudeLlmClient(AppProperties props) {
        this.config = props.llm();
    }

    @Override
    public <T> T generate(LlmRequest request, Class<T> type) {
        String apiKey = System.getenv("ANTHROPIC_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("ANTHROPIC_API_KEY is not set; using local fallback assessment data for task: {}", request.task());
            return LocalMockLlmFallback.generate(request.task(), type);
        }

        StructuredMessageCreateParams.Builder<T> builder = MessageCreateParams.builder()
                .model(config.model())
                .maxTokens(config.maxTokens())
                .thinking(ThinkingConfigAdaptive.builder().build())
                .system(request.system())
                .outputConfig(StructuredOutputConfig.<T>builder()
                        .format(type)
                        .effort(OutputConfig.Effort.HIGH)
                        .build())
                .addUserMessage(request.user());
        if (config.refusalFallbacks()) {
            builder.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
                    .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"));
        }

        StructuredMessage<T> response;
        try {
            response = client().messages().create(builder.build());
        } catch (AnthropicException e) {
            throw new LlmException("LLM call failed for task '%s': %s".formatted(request.task(), e.getMessage()), e);
        }

        StopReason stopReason = response.stopReason().orElse(null);
        log.info("LLM task={} stopReason={} inputTokens={} outputTokens={}", request.task(), stopReason,
                response.usage().inputTokens(), response.usage().outputTokens());
        if (StopReason.REFUSAL.equals(stopReason)) {
            throw new LlmException("LLM declined task '%s'".formatted(request.task()));
        }
        if (StopReason.MAX_TOKENS.equals(stopReason)) {
            throw new LlmException("LLM output for task '%s' was truncated at max_tokens=%d"
                    .formatted(request.task(), config.maxTokens()));
        }
        return response.content().stream()
                .flatMap(block -> block.text().stream())
                .map(text -> text.text())
                .findFirst()
                .orElseThrow(() -> new LlmException("LLM returned no structured output for task '%s'"
                        .formatted(request.task())));
    }

    private AnthropicClient client() {
        AnthropicClient current = client;
        if (current == null) {
            synchronized (this) {
                if (client == null) {
                    client = AnthropicOkHttpClient.fromEnv();
                }
                current = client;
            }
        }
        return current;
    }
}
