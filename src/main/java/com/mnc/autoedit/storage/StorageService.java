package com.mnc.autoedit.storage;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;

public interface StorageService {
    URL presignUpload(String key, Duration expiresIn);
    URL presignDownload(String key, Duration expiresIn);
    boolean exists(String key);
    void downloadToFile(String key, Path targetPath);
    void uploadFile(String key, Path sourcePath, String contentType);
}

