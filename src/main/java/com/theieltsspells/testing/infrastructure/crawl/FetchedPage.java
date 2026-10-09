package com.theieltsspells.testing.infrastructure.crawl;

import java.net.URI;

public record FetchedPage(URI uri, String contentType, String body) {
}
