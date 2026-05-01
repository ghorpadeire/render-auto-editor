package com.mnc.autoedit.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URL;
import java.nio.file.Path;
import java.time.Duration;

@Service
@ConditionalOnExpression(
        "'${storage.bucket:}' != '' && '${storage.access-key:}' != '' && '${storage.secret-key:}' != ''"
)
public class S3StorageService implements StorageService {
    private final S3Client s3;
    private final S3Presigner presigner;
    private final S3StorageConfig cfg;

    public S3StorageService(S3Client s3Client, S3Presigner s3Presigner, S3StorageConfig config) {
        this.s3 = s3Client;
        this.presigner = s3Presigner;
        this.cfg = config;
    }

    @Override
    public URL presignUpload(String key, Duration expiresIn) {
        var put = PutObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                .contentType("video/mp4")
                .build();
        PresignedPutObjectRequest req = presigner.presignPutObject(
                PutObjectPresignRequest.builder().signatureDuration(expiresIn).putObjectRequest(put).build()
        );
        return req.url();
    }

    @Override
    public URL presignDownload(String key, Duration expiresIn) {
        var get = GetObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                .build();
        PresignedGetObjectRequest req = presigner.presignGetObject(
                GetObjectPresignRequest.builder().signatureDuration(expiresIn).getObjectRequest(get).build()
        );
        return req.url();
    }

    @Override
    public boolean exists(String key) {
        try {
            s3.headObject(HeadObjectRequest.builder().bucket(cfg.getBucket()).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void downloadToFile(String key, Path targetPath) {
        var req = GetObjectRequest.builder().bucket(cfg.getBucket()).key(key).build();
        s3.getObject(req, targetPath);
    }

    @Override
    public void uploadFile(String key, Path sourcePath, String contentType) {
        var req = PutObjectRequest.builder()
                .bucket(cfg.getBucket())
                .key(key)
                .contentType(contentType)
                .build();
        s3.putObject(req, RequestBody.fromFile(sourcePath));
    }
}

