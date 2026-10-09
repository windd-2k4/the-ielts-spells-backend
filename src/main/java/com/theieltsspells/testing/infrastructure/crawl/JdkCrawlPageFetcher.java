package com.theieltsspells.testing.infrastructure.crawl;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;

@Component
public class JdkCrawlPageFetcher implements CrawlPageFetcher {

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RESPONSE_BYTES = 5 * 1024 * 1024;

    private volatile HttpClient httpClient;

    @Override
    public FetchedPage fetch(URI uri) {
        URI current = uri;
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            validatePublicHttpUri(current);
            HttpRequest request = HttpRequest.newBuilder(current)
                    .timeout(Duration.ofSeconds(25))
                    .header("Accept", "text/html,application/xhtml+xml,application/json;q=0.8,text/plain;q=0.7")
                    .header("User-Agent", "TheIELTSSpells-CrawlHub/1.0 (+https://theieltsspells.io.vn)")
                    .GET()
                    .build();
            try {
                HttpResponse<byte[]> response = httpClient().send(request, HttpResponse.BodyHandlers.ofByteArray());
                if (response.statusCode() >= 300 && response.statusCode() < 400) {
                    String location = response.headers().firstValue("Location")
                            .orElseThrow(() -> new IllegalStateException("Nguồn crawl chuyển hướng nhưng thiếu Location"));
                    current = current.resolve(location);
                    continue;
                }
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException("Nguồn crawl trả HTTP " + response.statusCode() + ": " + current);
                }
                if (response.body().length > MAX_RESPONSE_BYTES) {
                    throw new IllegalStateException("Trang crawl vượt quá giới hạn 5 MB: " + current);
                }
                String contentType = response.headers().firstValue("Content-Type").orElse("text/html");
                if (!isSupportedContentType(contentType)) {
                    throw new IllegalStateException("Định dạng nguồn crawl không được hỗ trợ: " + contentType);
                }
                return new FetchedPage(response.uri(), contentType, new String(response.body(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalStateException("Không thể tải nguồn crawl: " + current, exception);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Tác vụ crawl bị gián đoạn", exception);
            }
        }
        throw new IllegalStateException("Nguồn crawl chuyển hướng quá nhiều lần: " + uri);
    }

    private HttpClient httpClient() {
        HttpClient existing = httpClient;
        if (existing != null) {
            return existing;
        }
        synchronized (this) {
            if (httpClient == null) {
                httpClient = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .followRedirects(HttpClient.Redirect.NEVER)
                        .build();
            }
            return httpClient;
        }
    }

    private boolean isSupportedContentType(String contentType) {
        String normalized = contentType.toLowerCase(Locale.ROOT);
        return normalized.contains("text/html")
                || normalized.contains("application/xhtml+xml")
                || normalized.contains("application/json")
                || normalized.contains("text/plain");
    }

    private void validatePublicHttpUri(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getUserInfo() != null) {
            throw new IllegalArgumentException("Nguồn crawl phải là URL HTTP(S) công khai hợp lệ");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.equals("localhost") || host.endsWith(".localhost") || host.endsWith(".local") || host.endsWith(".internal")) {
            throw new IllegalArgumentException("Không được crawl địa chỉ nội bộ");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (address.isAnyLocalAddress()
                        || address.isLoopbackAddress()
                        || address.isLinkLocalAddress()
                        || address.isSiteLocalAddress()
                        || address.isMulticastAddress()
                        || isUniqueLocalIpv6(address.getAddress())) {
                    throw new IllegalArgumentException("Không được crawl địa chỉ mạng riêng hoặc nội bộ");
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Không phân giải được tên miền nguồn crawl: " + host, exception);
        }
    }

    private boolean isUniqueLocalIpv6(byte[] bytes) {
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }
}
