package com.theieltsspells.dictionary.infrastructure;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.dictionary")
@Getter
@Setter
public class DictionaryProperties {
    private String seedPath = "./data/dictionary/dictionary_entries.jsonl.gz";
    private String sqliteBundlePath = "./data/dictionary/ielts_dict.sqlite";
    private String libretranslateUrl = "http://localhost:5000";
    private String libretranslateApiKey = "";
    private boolean seedOnStartup = true;
}
