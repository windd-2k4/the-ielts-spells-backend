package com.theieltsspells.dictionary.infrastructure;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
public class LibreTranslateClient {

    private final DictionaryProperties properties;
    private final RestClient restClient;

    public LibreTranslateClient(DictionaryProperties properties) {
        this.properties = properties;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2000);
        factory.setReadTimeout(8000);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    public Optional<String> translate(String text, String sourceLang, String targetLang) {
        String baseUrl = properties.getLibretranslateUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            return Optional.empty();
        }
        String endpoint = baseUrl.replaceAll("/+$", "") + "/translate";
        Map<String, Object> body = new HashMap<>();
        body.put("q", text);
        body.put("source", sourceLang);
        body.put("target", targetLang);
        body.put("format", "text");
        if (properties.getLibretranslateApiKey() != null && !properties.getLibretranslateApiKey().isBlank()) {
            body.put("api_key", properties.getLibretranslateApiKey());
        }

        try {
            JsonNode response = restClient.post()
                    .uri(endpoint)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            if (response != null && response.hasNonNull("translatedText")) {
                String translated = response.get("translatedText").asText("").trim();
                if (!translated.isBlank()) {
                    return Optional.of(translated);
                }
            }
        } catch (Exception ex) {
            log.debug("LibreTranslate unavailable at {}: {}", endpoint, ex.getMessage());
        }
        return Optional.empty();
    }
}
