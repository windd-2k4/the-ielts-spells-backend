package com.theieltsspells.testing.infrastructure.crawl;

import java.net.URI;

public interface CrawlPageFetcher {

    FetchedPage fetch(URI uri);
}
