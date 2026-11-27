package com.theieltsspells.shared.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class PublicApiUrlBuilder {
    private final String configuredBaseUrl;

    public PublicApiUrlBuilder(@Value("${app.public-api-base-url:}") String configuredBaseUrl) {
        this.configuredBaseUrl = trimTrailingSlashes(configuredBaseUrl);
    }

    public String url(String apiPath, Object... uriVariables) {
        var normalizedPath = apiPath.startsWith("/") ? apiPath : "/" + apiPath;
        UriComponentsBuilder builder = configuredBaseUrl.isBlank()
                ? UriComponentsBuilder.fromPath("/api/v1")
                : UriComponentsBuilder.fromUriString(configuredBaseUrl);
        return builder.path(normalizedPath)
                .buildAndExpand(uriVariables)
                .encode()
                .toUriString();
    }

    private static String trimTrailingSlashes(String value) {
        if (value == null) return "";
        return value.trim().replaceFirst("/+$", "");
    }
}
