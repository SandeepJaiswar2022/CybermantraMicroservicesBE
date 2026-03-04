package com.lms.content.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Async executor configuration for non-blocking file uploads.
 *
 * <p><b>Why a dedicated upload thread pool?</b>
 * Storage uploads (especially to Cloudinary or MinIO over the network) can take
 * 5–60+ seconds for large videos. If we ran uploads on Tomcat's request threads,
 * those threads would be blocked for the duration of each upload, quickly
 * exhausting the thread pool under concurrent load.
 *
 * <p>By offloading uploads to a dedicated pool:
 * <ul>
 *   <li>HTTP threads return immediately (202 Accepted) → high throughput.</li>
 *   <li>Upload threads are isolated — a slow upload doesn't affect API responsiveness.</li>
 *   <li>Pool size can be tuned independently of Tomcat's thread pool.</li>
 * </ul>
 *
 * <p>Sizing rationale:
 * <ul>
 *   <li>corePoolSize=5: 5 concurrent uploads always ready.</li>
 *   <li>maxPoolSize=20: scale up to 20 during bursts (network I/O bound, not CPU).</li>
 *   <li>queueCapacity=50: up to 50 uploads queued before rejection.</li>
 * </ul>
 */
@Configuration
@Slf4j
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "uploadExecutor")
    public Executor uploadExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("upload-");
        executor.setRejectedExecutionHandler((r, e) -> {
            // Log but don't throw — the DB record will stay UPLOADING and can be retried
            log.error("Upload task rejected — upload thread pool is saturated. Queue size={}",
                      e.getQueue().size());
        });
        executor.initialize();
        log.info("Upload executor initialized: core=5, max=20, queue=50");
        return executor;
    }

    @Override
    public Executor getAsyncExecutor() {
        return uploadExecutor();
    }
}
