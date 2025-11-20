package com.learning.authservice.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Redis wiring, and RedisScript bean that loads the CAS Lua script.
 */
@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        // Default: localhost:6379. Customize via properties or LettuceClientConfiguration.
        return new LettuceConnectionFactory();
    }

    @Bean
    public RedisTemplate<String, String> redisTemplate(LettuceConnectionFactory factory) {
        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setValueSerializer(stringSerializer);

        template.setHashKeySerializer(stringSerializer);
        template.setHashValueSerializer(stringSerializer);

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public DefaultRedisScript<String> refreshTokenRedisScript() throws Exception {
        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setResultType(String.class);

        // load lua from classpath (resources/lua/refresh_cas.lua)
        ClassPathResource r = new ClassPathResource("lua/refresh_cas.lua");
        try (InputStream is = r.getInputStream()) {
            String s = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            script.setScriptText(s);
        }
        return script;
    }
}
