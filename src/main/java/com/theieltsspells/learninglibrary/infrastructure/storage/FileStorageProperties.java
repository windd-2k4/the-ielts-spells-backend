package com.theieltsspells.learninglibrary.infrastructure.storage;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.storage")
public class FileStorageProperties {
    private String provider = "LOCAL";
    private long maxUploadBytes = 52_428_800;
    private String localDirectory = "./data/uploads";
    private String supabaseBucket = "learning-library";
}
