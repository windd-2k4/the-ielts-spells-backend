package com.theieltsspells.testing.application.crawl;

import com.theieltsspells.testing.application.dto.CrawlSourceDto;

import java.util.Set;

public interface OpenSourceTestCrawler {

    CrawlBatchResult crawl(CrawlSourceDto source, Set<String> knownSourceTestIds);
}
