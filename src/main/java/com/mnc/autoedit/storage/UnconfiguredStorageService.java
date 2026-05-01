package com.mnc.autoedit.storage;

import com.mnc.autoedit.debug.DebugNdjson;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;

@Service
@ConditionalOnExpression(
        "'${storage.bucket:}' == '' || '${storage.access-key:}' == '' || '${storage.secret-key:}' == ''"
)
public class UnconfiguredStorageService implements StorageService {

    private RuntimeException err() {
        // region agent log
        DebugNdjson.log(
                "pre-fix",
                "H2_start_without_storage",
                "UnconfiguredStorageService.java:22",
                "StorageService called but storage is not configured",
                DebugNdjson.data()
        );
        // endregion
        return new IllegalStateException("Storage is not configured. Set STORAGE_BUCKET, STORAGE_ACCESS_KEY, STORAGE_SECRET_KEY (and STORAGE_ENDPOINT if using R2).");
    }

    @Override
    public URL presignUpload(String key, Duration expiresIn) {
        throw err();
    }

    @Override
    public URL presignDownload(String key, Duration expiresIn) {
        throw err();
    }

    @Override
    public boolean exists(String key) {
        throw err();
    }

    @Override
    public void downloadToFile(String key, Path targetPath) {
        throw err();
    }

    @Override
    public void uploadFile(String key, Path sourcePath, String contentType) {
        throw err();
    }
}

