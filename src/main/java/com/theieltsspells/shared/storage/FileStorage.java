package com.theieltsspells.shared.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;

/** Public storage port used by modules that need managed object storage. */
public interface FileStorage {

    StoredFile store(String objectPath, MultipartFile file);

    StoredFile storeInBucket(String bucketName, String objectPath, MultipartFile file);

    StoredFile storeBytesInBucket(String bucketName, String objectPath, byte[] bytes, String contentType);

    InputStream openDefault(String objectPath);

    InputStream openFromBucket(String bucketName, String objectPath);

    record StoredFile(String provider, String bucketName, String objectPath) {}
}
