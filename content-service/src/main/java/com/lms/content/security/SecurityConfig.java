package com.lms.content.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.security.interfaces.RSAPublicKey;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Spring Security configuration for JWT validation using the shared RSA public key.
 *
 * <p>Architecture: The Auth Service signs tokens with its RSA private key.
 * This service (and all others) only hold the public key — they can verify
 * but never issue tokens. This follows the Asymmetric JWT pattern.
 *
 * <p>{@code @EnableMethodSecurity} enables {@code @PreAuthorize} on controllers.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@Slf4j
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.public-key-location}")
    private Resource publicKeyResource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())  // REST API; no session → no CSRF needed
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Actuator health check is public
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // All other endpoints require authentication (fine-grained via @PreAuthorize)
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );
        return http.build();
    }

    /**
     * Decodes and validates JWT signatures using the RSA public key.
     *
     * <p>The public key is loaded from classpath:keys/public.pem.
     * In production, this could instead point to the Auth Service JWKS endpoint
     * for automatic key rotation.
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        try {
            RSAPublicKey publicKey = RsaKeyUtil.loadPublicKey(publicKeyResource);
            log.info("RSA public key loaded for JWT validation");
            return NimbusJwtDecoder.withPublicKey(publicKey).build();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load RSA public key", e);
        }
    }

    /**
     * Extracts roles from the JWT {@code roles} claim and maps them to Spring
     * GrantedAuthority objects with the {@code ROLE_} prefix convention.
     *
     * <p>Example JWT claim: {@code "roles": ["INSTRUCTOR", "STUDENT"]}
     * → Spring authorities: {@code ROLE_INSTRUCTOR}, {@code ROLE_STUDENT}
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<String> roles = jwt.getClaimAsStringList("roles");
            if (roles == null) return List.of();
            return roles.stream()
                .map(role -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toList());
        });
        return converter;
    }
}
