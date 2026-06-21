package com.mobisec.in.apigateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.interfaces.RSAPublicKey;
import java.util.List;

/**
 * Security configuration for the API Gateway.
 *
 * <p>Implements OAuth2 Resource Server JWT validation using an RSA public key.
 * Public endpoints are explicitly permitted; all others require a valid JWT.
 *
 * <p>CORS is configured globally to allow the React frontends running on
 * localhost:3000 (CRA) and localhost:5173 (Vite).
 */
@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    /**
     * Classpath resource pointing to the RSA public key in PEM format.
     * The same private key used by Auth Service to sign JWTs must correspond
     * to this public key.
     */
    @Value("${jwt.public-key-path}")
    private Resource publicKeyResource;

    /**
     * Endpoints that do not require authentication.
     * Auth endpoints, email verification, and the actuator health check
     * are open to unauthenticated traffic.
     */
    private static final String[] PUBLIC_ENDPOINTS = {
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/verify-email/**",
            "/actuator/health",
            "/actuator/info",
            "/fallback/**"
    };

    /**
     * Configures the main security filter chain for WebFlux.
     *
     * @param http          the reactive HTTP security builder
     * @param jwtDecoder    the reactive JWT decoder backed by RSA public key
     * @return the configured security filter chain
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            ReactiveJwtDecoder jwtDecoder) {

        return http
                // Disable CSRF — stateless JWT-based API does not need it
                .csrf(ServerHttpSecurity.CsrfSpec::disable)

                // Apply global CORS configuration
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // Route-level authorization rules
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .anyExchange().authenticated()
                )

                // Configure OAuth2 Resource Server to validate JWTs
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtDecoder(jwtDecoder))
                )

                .build();
    }

    /**
     * Creates a reactive JWT decoder that validates tokens using the RSA public key.
     *
     * @return configured {@link NimbusReactiveJwtDecoder}
     * @throws IOException          if the PEM file cannot be read
     * @throws Exception            if RSA key parsing fails
     */
    @Bean
    public ReactiveJwtDecoder reactiveJwtDecoder() throws Exception {
        RSAPublicKey publicKey = loadRsaPublicKey();
        return NimbusReactiveJwtDecoder.withPublicKey(publicKey).build();
    }

    /**
     * Reads and parses the RSA public key from the PEM file on the classpath.
     *
     * @return the parsed {@link RSAPublicKey}
     * @throws Exception if reading or parsing fails
     */
    private RSAPublicKey loadRsaPublicKey() throws Exception {
        String pemContent = publicKeyResource.getContentAsString(StandardCharsets.UTF_8);

        // Strip PEM headers and decode Base64
        String stripped = pemContent
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");

        byte[] decoded = java.util.Base64.getDecoder().decode(stripped);

        java.security.spec.X509EncodedKeySpec keySpec =
                new java.security.spec.X509EncodedKeySpec(decoded);

        java.security.KeyFactory keyFactory =
                java.security.KeyFactory.getInstance("RSA");

        return (RSAPublicKey) keyFactory.generatePublic(keySpec);
    }

    /**
     * Configures global CORS policy allowing the React frontends to communicate
     * with the gateway, including credential-bearing requests (cookies, Authorization headers).
     *
     * @return reactive CORS configuration source
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // Allowed frontend origins
        config.setAllowedOrigins(List.of(
                "http://localhost:3000",   // Create React App
                "http://localhost:5173"    // Vite
        ));

        // Allow all standard HTTP methods
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));

        // Allow all headers (Authorization, Content-Type, X-Request-ID, etc.)
        config.setAllowedHeaders(List.of("*"));

        // Expose correlation ID to frontend clients
        config.setExposedHeaders(List.of("X-Request-ID", "X-Correlation-ID"));

        // Allow credentials (JWT in Authorization header / cookies)
        config.setAllowCredentials(true);

        // Cache preflight response for 1 hour
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
