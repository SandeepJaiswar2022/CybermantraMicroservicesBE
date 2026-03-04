package com.lms.content;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Content Service - manages video uploads, resources, thumbnails, and secure streaming.
 *
 * <p>Architecture decisions:
 * <ul>
 *   <li>Async upload to avoid blocking HTTP threads during large file transfers</li>
 *   <li>Streaming responses to avoid loading video data into JVM heap</li>
 *   <li>Event publishing via RabbitMQ for downstream processing (transcoding, notifications)</li>
 *   <li>Storage abstraction layer allowing hot-swap between Cloudinary and MinIO</li>
 * </ul>
 */
@SpringBootApplication
@EnableAsync          // enables @Async for non-blocking upload processing
@EnableCaching        // enables @Cacheable for content metadata
public class ContentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ContentServiceApplication.class, args);
    }
}
