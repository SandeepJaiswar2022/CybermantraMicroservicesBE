package com.lms.content.config;

import com.cloudinary.Cloudinary;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Map;

/**
 * Cloudinary SDK configuration.
 * Only loaded when {@code content.storage.provider=cloudinary}.
 */
@Configuration
@Slf4j
@ConditionalOnProperty(name = "content.storage.provider", havingValue = "cloudinary")
public class CloudinaryConfig {

    @Value("${cloudinary.cloud-name}")
    private String cloudName;

    @Value("${cloudinary.api-key}")
    private String apiKey;

    @Value("${cloudinary.api-secret}")
    private String apiSecret;

    @Value("${cloudinary.signed-url-expiry-seconds:3600}")
    private long signedUrlExpirySeconds;

    @Bean
    public Cloudinary cloudinary() {
        log.info("Initializing Cloudinary client for cloud=[{}]", cloudName);
        return new Cloudinary(Map.of(
            "cloud_name", cloudName,
            "api_key",    apiKey,
            "api_secret", apiSecret,
            "secure",     true      // always use https
        ));
    }

    @Bean
    public long signedUrlExpirySeconds() {
        return signedUrlExpirySeconds;
    }
}
