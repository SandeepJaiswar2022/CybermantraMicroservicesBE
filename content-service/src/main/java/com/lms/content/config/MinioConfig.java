package com.lms.content.config;

import com.lms.content.config.MinioProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MinIO client configuration.
 *
 * <p>On startup, ensures all required buckets exist (auto-creates if missing).
 * This is safe to do on every startup — idempotent operation.
 *
 * <p>Only loaded when {@code content.storage.provider=minio}.
 */
@Configuration
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(name = "content.storage.provider", havingValue = "minio")
public class MinioConfig {

    private final MinioProperties minioProperties;

    @Bean
    public MinioClient minioClient() throws Exception {
        log.info("Initializing MinIO client: endpoint=[{}]", minioProperties.getEndpoint());

        MinioClient client = MinioClient.builder()
            .endpoint(minioProperties.getEndpoint())
            .credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
            .build();

        // Auto-create buckets on startup
        ensureBucketExists(client, minioProperties.getBucket().getVideos());
        ensureBucketExists(client, minioProperties.getBucket().getResources());
        ensureBucketExists(client, minioProperties.getBucket().getThumbnails());

        log.info("MinIO client initialized successfully");
        return client;
    }

    private void ensureBucketExists(MinioClient client, String bucket) throws Exception {
        boolean exists = client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (!exists) {
            client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
            log.info("Created MinIO bucket: [{}]", bucket);
        } else {
            log.debug("MinIO bucket already exists: [{}]", bucket);
        }
    }
}
