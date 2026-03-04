package com.lms.content.config;

import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Redis cache configuration.
 *
 * <p>Cached data:
 * <ul>
 *   <li>{@code lectureContent} — list of content items per lecture, TTL 5 min.
 *       Evicted on every upload/delete to that lecture.</li>
 * </ul>
 *
 * <p>Why NOT cache signed URLs:
 * Signed URLs are already time-limited. Caching them in Redis would reduce their
 * effective security window without meaningful performance gain (generation is ~1ms).
 *
 * <p>Serialization: Jackson JSON (not Java serialization) for:
 * <ul>
 *   <li>Human-readable cache inspection via redis-cli.</li>
 *   <li>Cross-language compatibility if another service reads the cache.</li>
 *   <li>No class versioning issues on rolling deploys.</li>
 * </ul>
 */
@Configuration
public class CacheConfig {

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return builder -> builder
            .withCacheConfiguration(
                "lectureContent",
                RedisCacheConfiguration.defaultCacheConfig()
                    .entryTtl(Duration.ofMinutes(5))
                    .serializeValuesWith(
                        RedisSerializationContext.SerializationPair.fromSerializer(
                            new GenericJackson2JsonRedisSerializer()
                        )
                    )
            );
    }
}
