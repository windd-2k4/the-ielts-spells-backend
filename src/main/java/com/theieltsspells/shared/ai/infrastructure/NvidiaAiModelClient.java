package com.theieltsspells.shared.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiGenerationRequest;
import com.theieltsspells.shared.ai.AiGenerationResult;
import com.theieltsspells.shared.ai.AiModelClient;
import com.theieltsspells.shared.ai.AiProvider;
import com.theieltsspells.shared.ai.AiProviderException;
import com.theieltsspells.shared.ai.AiRoutingProperties;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class NvidiaAiModelClient extends AbstractHttpAiModelClient implements AiModelClient {

    public NvidiaAiModelClient(ObjectMapper objectMapper, AiRoutingProperties properties) {
        super(objectMapper, properties);
    }

    @Override
    public AiProvider provider() {
        return AiProvider.NVIDIA;
    }

    @Override
    public boolean isConfigured() {
        return !properties.getNvidia().getApiKey().isBlank();
    }

    @Override
    public AiGenerationResult generate(String model, AiGenerationRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
                Map.of("role", "system", "content", request.systemPrompt()),
                Map.of("role", "user", "content", request.userPrompt())
        ));
        applyModelOptions(body, model, request);
        body.put("max_tokens", request.maxOutputTokens());
        body.put("stream", false);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.setBearerAuth(properties.getNvidia().getApiKey());

        String url = stripTrailingSlash(properties.getNvidia().getBaseUrl()) + "/chat/completions";
        try {
            String rawResponse = client(request.timeout()).postForObject(
                    url, new HttpEntity<>(body, headers), String.class
            );
            Map<String, Object> response = readObject(rawResponse);
            List<?> choices = list(response.get("choices"));
            if (choices.isEmpty()) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                        "NVIDIA response has no choices");
            }
            Map<?, ?> choice = map(choices.getFirst());
            if ("length".equalsIgnoreCase(text(choice.get("finish_reason")))) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                        "NVIDIA response was truncated");
            }
            String content = text(map(choice.get("message")).get("content"));
            if (content.isBlank()) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                        "NVIDIA response has no content");
            }
            return new AiGenerationResult(provider(), model, content);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw translate("NVIDIA", model, exception);
        }
    }

    static void applyModelOptions(Map<String, Object> body, String model, AiGenerationRequest request) {
        String normalizedModel = model == null ? "" : model.toLowerCase(Locale.ROOT);
        boolean configurableNemotron = normalizedModel.startsWith("nvidia/nemotron-3")
                || normalizedModel.startsWith("nvidia/nemotron-4")
                || normalizedModel.startsWith("nvidia/nemotron-nano-3");
        if (request.disableReasoning() && configurableNemotron) {
            body.put("chat_template_kwargs", Map.of("enable_thinking", false));
            body.put("temperature", 1.0);
            body.put("top_p", 0.95);
            return;
        }
        if (request.temperature() != null) body.put("temperature", request.temperature());
        body.put("top_p", 0.9);
    }

    private static String stripTrailingSlash(String value) {
        return value == null ? "" : value.replaceFirst("/+$", "");
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private static List<?> list(Object value) {
        return value instanceof List<?> values ? values : List.of();
    }

    private static Map<?, ?> map(Object value) {
        return value instanceof Map<?, ?> result ? result : Map.of();
    }
}
