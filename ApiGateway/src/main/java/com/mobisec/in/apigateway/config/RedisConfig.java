package com.mobisec.in.apigateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration for the API Gateway.
 *
 * Spring Boot auto-configures the ReactiveRedisConnectionFactory from
 * application.yml (spring.data.redis.*), so we only define the template
 * bean here, injecting the auto-configured factory by name.
 */
@Configuration
public class RedisConfig {

    /**
     * Reactive Redis template using String serializers for both keys and values.
     * Compatible with Spring Cloud Gateway's RequestRateLimiter filter.
     *
     * Spring Boot's LettuceConnectionConfiguration auto-creates the factory bean
     * named "redisConnectionFactory" — we inject it explicitly here.
     */
    @Bean
    public ReactiveRedisTemplate<String, String> reactiveRedisTemplate(
            ReactiveRedisConnectionFactory redisConnectionFactory) {

        StringRedisSerializer serializer = new StringRedisSerializer();

        RedisSerializationContext<String, String> serializationContext =
                RedisSerializationContext.<String, String>newSerializationContext()
                        .key(serializer)
                        .value(serializer)
                        .hashKey(serializer)
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(redisConnectionFactory, serializationContext);
    }
}