package com.lms.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Type-safe binding for MinIO configuration from application.yml.
 */
@Component
@ConfigurationProperties(prefix = "minio")
@Data
public class MinioProperties {

    private String endpoint;
    private String accessKey;
    private String secretKey;
    private long presignedUrlExpiryMinutes = 60;
    private Bucket bucket = new Bucket();

    @Data
    public static class Bucket {
        private String videos;
        private String resources;
        private String thumbnails;
    }
}
