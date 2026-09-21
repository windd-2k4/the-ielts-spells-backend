package com.theieltsspells.shared.ai.infrastructure;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.theieltsspells.shared.ai.AiProviderException;
import com.theieltsspells.shared.ai.AiRoutingProperties;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

abstract class AbstractHttpAiModelClient {

    protected final ObjectMapper objectMapper;
    protected final AiRoutingProperties properties;
    private final Map<Long, RestTemplate> clients = new ConcurrentHashMap<>();

    protected AbstractHttpAiModelClient(ObjectMapper objectMapper, AiRoutingProperties properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    protected RestTemplate client(Duration readTimeout) {
        long timeoutMillis = Math.max(1_000, Math.min(readTimeout.toMillis(), 600_000));
        return clients.computeIfAbsent(timeoutMillis, ignored -> {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            int connectMillis = Math.max(1_000, Math.min(properties.getConnectTimeoutSeconds() * 1_000, 60_000));
            factory.setConnectTimeout(Duration.ofMillis(connectMillis));
            factory.setReadTimeout(Duration.ofMillis(timeoutMillis));
            return new RestTemplate(factory);
        });
    }

    protected Map<String, Object> readObject(String json) {
        if (json == null || json.isBlank()) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                    "Provider returned an empty response");
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() { });
        } catch (Exception exception) {
            throw new AiProviderException(AiProviderException.Kind.INVALID_RESPONSE,
                    "Provider returned malformed JSON", exception);
        }
    }

    protected AiProviderException translate(String provider, String model, RestClientException exception) {
        String prefix = provider + " model " + model + " request failed";
        if (exception instanceof ResourceAccessException) {
            return new AiProviderException(AiProviderException.Kind.TRANSIENT,
                    prefix + " because of a timeout or network error", exception);
        }
        if (exception instanceof HttpStatusCodeException statusException) {
            HttpStatusCode status = statusException.getStatusCode();
            if (status.value() == 404) {
                return new AiProviderException(AiProviderException.Kind.MODEL_UNAVAILABLE,
                        prefix + " because the model was not found", exception);
            }
            if (status.value() == 401 || status.value() == 403) {
                return new AiProviderException(AiProviderException.Kind.PROVIDER_CONFIGURATION,
                        prefix + " because provider credentials or permissions are invalid", exception);
            }
            if (status.value() == 408 || status.value() == 409 || status.value() == 429 || status.is5xxServerError()) {
                return new AiProviderException(AiProviderException.Kind.TRANSIENT,
                        prefix + " with transient HTTP status " + status.value(), exception);
            }
            return new AiProviderException(AiProviderException.Kind.INVALID_REQUEST,
                    prefix + " with non-retryable HTTP status " + status.value(), exception);
        }
        return new AiProviderException(AiProviderException.Kind.TRANSIENT, prefix, exception);
    }
}
