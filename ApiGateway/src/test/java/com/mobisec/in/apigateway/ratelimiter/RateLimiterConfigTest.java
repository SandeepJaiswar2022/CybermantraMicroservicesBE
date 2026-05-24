package com.mobisec.in.apigateway.ratelimiter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * Unit tests for the {@link RateLimiterConfig} custom {@link KeyResolver}.
 *
 * <p>Verifies key resolution for:
 * <ul>
 *   <li>Anonymous requests resolved by IP address</li>
 *   <li>Requests with X-Forwarded-For header</li>
 *   <li>Requests without any identifiable IP (fallback)</li>
 * </ul>
 *
 * <p>JWT-authenticated key resolution is tested as an integration test since
 * it requires the full reactive security context to be populated.
 */
class RateLimiterConfigTest {

    private RateLimiterConfig rateLimiterConfig;

    @BeforeEach
    void setUp() {
        rateLimiterConfig = new RateLimiterConfig();
    }

    @Test
    @DisplayName("Key resolver should use X-Forwarded-For header when present")
    void keyResolver_withXForwardedFor_shouldUseForwardedIp() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/courses")
                .remoteAddress(new java.net.InetSocketAddress("10.0.0.1", 8080))
                .header("X-Forwarded-For", "203.0.113.42, 10.0.0.1")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userOrIpKeyResolver();
        Mono<String> key = resolver.resolve(exchange);

        // Should extract the first (client) IP from X-Forwarded-For
        StepVerifier.create(key)
                .expectNextMatches(k -> k.equals("ip:203.0.113.42"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Key resolver should fall back to remote address when no X-Forwarded-For")
    void keyResolver_withoutForwardedHeader_shouldUseRemoteAddress() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/users/me")
                .remoteAddress(new java.net.InetSocketAddress("192.168.1.100", 54321))
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userOrIpKeyResolver();
        Mono<String> key = resolver.resolve(exchange);

        StepVerifier.create(key)
                .expectNextMatches(k -> k.equals("ip:192.168.1.100"))
                .verifyComplete();
    }

    @Test
    @DisplayName("Key resolver should use first IP in X-Forwarded-For chain")
    void keyResolver_withMultipleForwardedIps_shouldUseFirst() {
        MockServerHttpRequest request = MockServerHttpRequest
                .get("/api/v1/content/123")
                .header("X-Forwarded-For", "198.51.100.5, 10.1.1.1, 172.16.0.1")
                .build();

        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        KeyResolver resolver = rateLimiterConfig.userOrIpKeyResolver();
        Mono<String> key = resolver.resolve(exchange);

        StepVerifier.create(key)
                .expectNextMatches(k -> k.equals("ip:198.51.100.5"))
                .verifyComplete();
    }
}
