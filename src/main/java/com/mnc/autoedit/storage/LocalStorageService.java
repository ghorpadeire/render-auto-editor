package com.mnc.autoedit.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;

@Service
@ConditionalOnExpression(
        "'${storage.bucket:}' == '' || '${storage.access-key:}' == '' || '${storage.secret-key:}' == ''"
)
public class LocalStorageService implements StorageService {

    private final Path root;

    public LocalStorageService() {
        this.root = Path.of(System.getProperty("java.io.tmpdir"), "autoedit-storage");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create local storage dir: " + root, e);
        }
    }

    public Path resolve(String key) {
        return root.resolve(key);
    }

    public void saveUpload(String key, byte[] data) throws IOException {
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, data);
    }

    @Override
    public URL presignUpload(String key, Duration expiresIn) {
        throw new IllegalStateException("Direct upload mode — use /v1/direct/upload instead of presigned URLs.");
    }

    @Override
    public URL presignDownload(String key, Duration expiresIn) {
        throw new IllegalStateException("Direct download mode — use /v1/direct/download/{jobId} instead of presigned URLs.");
    }

    @Override
    public boolean exists(String key) {
        return Files.exists(resolve(key));
    }

    @Override
    public void downloadToFile(String key, Path targetPath) {
        Path source = resolve(key);
        try {
            Files.createDirectories(targetPath.getParent());
            Files.copy(source, targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Local storage download failed: " + key, e);
        }
    }

    @Override
    public void uploadFile(String key, Path sourcePath, String contentType) {
        Path target = resolve(key);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(sourcePath, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Local storage upload failed: " + key, e);
        }
    }
}
