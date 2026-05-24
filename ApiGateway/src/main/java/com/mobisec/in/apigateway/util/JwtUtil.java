package com.mobisec.in.apigateway.util;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.server.reactive.ServerHttpRequest;

import java.util.Base64;
import java.util.Optional;

/**
 * Utility class for extracting claims from JWT tokens without full validation.
 *
 * <p><strong>Important:</strong> This utility is for <em>informational purposes only</em>
 * (e.g. logging the user ID). Full cryptographic JWT validation is performed by
 * Spring Security's OAuth2 Resource Server before any filter can read claims.
 *
 * <p>Do NOT use this class for authorization decisions — use the authenticated
 * principal from the security context instead.
 */
@Slf4j
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class JwtUtil {

    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Extracts the raw JWT token string from the Authorization header.
     *
     * @param request the incoming server HTTP request
     * @return an Optional containing the raw JWT string, or empty if not present
     */
    public static Optional<String> extractToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return Optional.of(authHeader.substring(BEARER_PREFIX.length()).trim());
        }

        return Optional.empty();
    }

    /**
     * Extracts the subject (user ID) claim from a JWT payload without validating
     * the signature. Only call this after Spring Security has already validated the token.
     *
     * @param jwtToken the raw JWT string (header.payload.signature)
     * @return an Optional containing the subject string, or empty if extraction fails
     */
    public static Optional<String> extractSubjectUnsafe(String jwtToken) {
        try {
            String[] parts = jwtToken.split("\\.");
            if (parts.length != 3) {
                return Optional.empty();
            }

            // Decode the payload (second segment) from Base64URL
            byte[] payloadBytes = Base64.getUrlDecoder().decode(padBase64(parts[1]));
            String payload = new String(payloadBytes);

            // Simple string extraction — avoids pulling in a JSON library dependency
            // For full parsing, use the authenticated Jwt principal from security context
            int subStart = payload.indexOf("\"sub\":\"");
            if (subStart == -1) {
                return Optional.empty();
            }
            subStart += 7; // length of "\"sub\":\""
            int subEnd = payload.indexOf('"', subStart);
            if (subEnd == -1) {
                return Optional.empty();
            }

            return Optional.of(payload.substring(subStart, subEnd));

        } catch (Exception e) {
            log.debug("Could not extract subject from JWT payload: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Pads a Base64URL string to ensure it is a multiple of 4 characters,
     * as required by the Java Base64 decoder.
     */
    private static String padBase64(String base64Url) {
        int padding = 4 - (base64Url.length() % 4);
        if (padding < 4) {
            return base64Url + "=".repeat(padding);
        }
        return base64Url;
    }
}
