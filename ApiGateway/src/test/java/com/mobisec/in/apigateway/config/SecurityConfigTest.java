package com.mobisec.in.apigateway.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * Integration tests for the Security configuration.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Public endpoints are accessible without authentication</li>
 *   <li>Protected endpoints return 401 when no token is provided</li>
 *   <li>Actuator health endpoint is publicly accessible</li>
 * </ul>
 *
 * <p>Uses {@code @SpringBootTest} with WebFlux test client to exercise the
 * full reactive security filter chain.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Use a test RSA key pair — override the real key location
        "spring.security.oauth2.resourceserver.jwt.public-key-location=classpath:test-public.pem",
        // Disable Redis for unit tests
        "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration," +
                "org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
        // Disable rate limiter in tests
        "spring.cloud.gateway.default-filters="
})
class SecurityConfigTest {

    @Autowired
    private WebTestClient webTestClient;

    @Test
    @DisplayName("Actuator health endpoint should be publicly accessible")
    void actuatorHealth_shouldBePublic() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("Protected endpoint should return 401 without Authorization header")
    void protectedEndpoint_withoutToken_shouldReturn401() {
        webTestClient.get()
                .uri("/api/v1/courses")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Protected endpoint should return 401 with malformed JWT")
    void protectedEndpoint_withBadToken_shouldReturn401() {
        webTestClient.get()
                .uri("/api/v1/users/me")
                .header("Authorization", "Bearer this.is.not.a.valid.jwt")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    @Test
    @DisplayName("Auth login endpoint should be publicly accessible (not blocked by security)")
    void authLogin_shouldBePermittedWithoutToken() {
        // The gateway will forward to Auth Service; without Auth Service running
        // it will return 503 or connect refused — but NOT 401 from the gateway itself.
        // We verify the gateway's security layer does not reject the request.
        webTestClient.post()
                .uri("/api/v1/auth/login")
                .exchange()
                // Not 401 — could be 503 (downstream unavailable) or 200
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("Auth register endpoint should be publicly accessible")
    void authRegister_shouldBePermittedWithoutToken() {
        webTestClient.post()
                .uri("/api/v1/auth/register")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("Auth refresh endpoint should be publicly accessible")
    void authRefresh_shouldBePermittedWithoutToken() {
        webTestClient.post()
                .uri("/api/v1/auth/refresh")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }
}
