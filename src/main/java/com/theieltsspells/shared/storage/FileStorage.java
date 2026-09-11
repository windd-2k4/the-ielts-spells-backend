package com.theieltsspells.shared.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/** Public storage port used by modules that need managed object storage. */
public interface FileStorage {

    StoredFile store(String objectPath, MultipartFile file);

    InputStream openDefault(String objectPath);

    record StoredFile(String provider, String bucketName, String objectPath) {}
}
