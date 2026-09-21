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
import java.util.Map;

@Component
public class GeminiAiModelClient extends AbstractHttpAiModelClient implements AiModelClient {

    public GeminiAiModelClient(ObjectMapper objectMapper, AiRoutingProperties properties) {
        super(objectMapper, properties);
    }

    @Override
    public AiProvider provider() {
        return AiProvider.GEMINI;
    }

    @Override
    public boolean isConfigured() {
        return !properties.getGemini().getApiKey().isBlank();
    }

    @Override
    public AiGenerationResult generate(String model, AiGenerationRequest request) {
        Map<String, Object> generationConfig = new LinkedHashMap<>();
        if (request.responseMimeType() != null && !request.responseMimeType().isBlank()) {
            generationConfig.put("responseMimeType", request.responseMimeType());
        }
        generationConfig.put("maxOutputTokens", request.maxOutputTokens());
        if (request.temperature() != null && !model.startsWith("gemini-3.5")) {
            generationConfig.put("temperature", request.temperature());
        }
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of(
                        "text", request.systemPrompt() + "\n" + request.userPrompt()
                )))),
                "generationConfig", generationConfig
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        headers.set("x-goog-api-key", properties.getGemini().getApiKey());

        String url = stripTrailingSlash(properties.getGemini().getBaseUrl())
                + "/models/" + model + ":generateContent";
        try {
            String rawResponse = client(request.timeout()).postForObject(
                    url, new HttpEntity<>(body, headers), String.class
            );
            Map<String, Object> response = readObject(rawResponse);
            List<?> candidates = list(response.get("candidates"));
            if (candidates.isEmpty()) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                        "Gemini response has no candidates");
            }
            Map<?, ?> candidate = map(candidates.getFirst());
            if ("MAX_TOKENS".equalsIgnoreCase(text(candidate.get("finishReason")))) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                        "Gemini response was truncated");
            }
            List<?> parts = list(map(candidate.get("content")).get("parts"));
            if (parts.isEmpty()) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                        "Gemini response has no content");
            }
            String content = text(map(parts.getFirst()).get("text"));
            if (content.isBlank()) {
                throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                        "Gemini response has empty content");
            }
            return new AiGenerationResult(provider(), model, content);
        } catch (AiProviderException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw translate("Gemini", model, exception);
        }
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
