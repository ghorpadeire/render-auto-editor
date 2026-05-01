package com.mnc.autoedit.storage;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.mnc.autoedit.debug.DebugNdjson;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

@Configuration
@EnableConfigurationProperties(S3StorageConfig.class)
public class S3ClientsConfig {

    @Bean
    @ConditionalOnExpression("'${storage.bucket:}'.length() > 0 and '${storage.access-key:}'.length() > 0 and '${storage.secret-key:}'.length() > 0")
    S3Client s3Client(S3StorageConfig cfg) {
        // region agent log
        DebugNdjson.log(
                "pre-fix",
                "H1_missing_storage_env",
                "S3ClientsConfig.java:24",
                "Creating S3Client (config presence)",
                DebugNdjson.data()
                        .put("bucketBlank", cfg.getBucket() == null || cfg.getBucket().isBlank())
                        .put("accessKeyBlank", cfg.getAccessKey() == null || cfg.getAccessKey().isBlank())
                        .put("secretKeyBlank", cfg.getSecretKey() == null || cfg.getSecretKey().isBlank())
                        .put("endpointBlank", cfg.getEndpoint() == null || cfg.getEndpoint().isBlank())
                        .put("presignEndpointBlank", cfg.getPresignEndpoint() == null || cfg.getPresignEndpoint().isBlank())
        );
        // endregion
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey()));

        var builder = S3Client.builder()
                .credentialsProvider(creds)
                .region(Region.of(cfg.getRegion() == null || cfg.getRegion().isBlank() ? "auto" : cfg.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());

        if (cfg.getEndpoint() != null && !cfg.getEndpoint().isBlank()) {
            builder = builder.endpointOverride(URI.create(cfg.getEndpoint()));
        }
        return builder.build();
    }

    @Bean
    @ConditionalOnExpression("'${storage.bucket:}'.length() > 0 and '${storage.access-key:}'.length() > 0 and '${storage.secret-key:}'.length() > 0")
    S3Presigner s3Presigner(S3StorageConfig cfg) {
        // region agent log
        DebugNdjson.log(
                "pre-fix",
                "H1_missing_storage_env",
                "S3ClientsConfig.java:47",
                "Creating S3Presigner",
                DebugNdjson.data().put("hasPresignEndpoint", cfg.getPresignEndpoint() != null && !cfg.getPresignEndpoint().isBlank())
        );
        // endregion
        var creds = StaticCredentialsProvider.create(AwsBasicCredentials.create(cfg.getAccessKey(), cfg.getSecretKey()));
        var builder = S3Presigner.builder()
                .credentialsProvider(creds)
                .region(Region.of(cfg.getRegion() == null || cfg.getRegion().isBlank() ? "auto" : cfg.getRegion()))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());

        String endpoint = (cfg.getPresignEndpoint() != null && !cfg.getPresignEndpoint().isBlank())
                ? cfg.getPresignEndpoint()
                : cfg.getEndpoint();
        if (endpoint != null && !endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(endpoint));
        }
        return builder.build();
    }
}

