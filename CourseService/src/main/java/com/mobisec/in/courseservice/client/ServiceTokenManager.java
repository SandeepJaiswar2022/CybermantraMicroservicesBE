package com.mobisec.in.courseservice.client;

import com.mobisec.in.courseservice.dto.internal.ServiceTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class ServiceTokenManager {

    private final RestTemplate restTemplate;

    @Value("${service.name}")
    private String serviceName;

    @Value("${service.secret}")
    private String serviceSecret;

    @Value("${internal.auth-service-url}")
    private String authServiceUrl;

    // Cached token state — volatile for visibility across threads
    private volatile String cachedToken = null;
    private volatile Instant tokenExpiry = Instant.EPOCH;

    // Refresh buffer — refresh 60 seconds before actual expiry
    private static final long REFRESH_BUFFER_SECONDS = 60;

    /**
     * Returns a valid service token.
     * Refreshes automatically if expired or within buffer window.
     * Thread-safe via double-checked locking.
     */
    public String getServiceToken() {
        if (needsRefresh()) {
            refreshToken();
        }
        return cachedToken;
    }

    private boolean needsRefresh() {
        return cachedToken == null ||
                Instant.now().isAfter(tokenExpiry.minusSeconds(REFRESH_BUFFER_SECONDS));
    }

    private synchronized void refreshToken() {
        // Double-check after acquiring lock — another thread may have refreshed already
        if (!needsRefresh()) {
            return;
        }

        log.info("Refreshing service token for: {}", serviceName);

        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            // Service authenticates with name + secret — no JWT yet at this point
            Map<String, String> body = Map.of(
                    "serviceName", serviceName,
                    "serviceSecret", serviceSecret
            );

            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(body, headers);

            ResponseEntity<Map<String, Object>> response = restTemplate.exchange(
                    authServiceUrl + "/api/v1/internal/auth/service-token",
                    HttpMethod.POST,
                    requestEntity,
                    new ParameterizedTypeReference<>() {
                    }
            );

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                // ApiResponse wrapper — data field contains ServiceTokenResponse
                @SuppressWarnings("unchecked")
                Map<String, Object> data = (Map<String, Object>) response.getBody().get("data");

                this.cachedToken = (String) data.get("token");
                long expiresInSeconds = ((Number) data.get("expiresInSeconds")).longValue();

                // Set expiry based on auth-service reported expiry
                this.tokenExpiry = Instant.now().plusSeconds(expiresInSeconds);

                log.info("Service token refreshed successfully. Expires at: {}", tokenExpiry);
            } else {
                throw new RuntimeException("Unexpected response from auth-service");
            }

        } catch (Exception e) {
            // If refresh fails, but we still have a non-expired token, keep using it
            // This prevents auth-service downtime from cascading into course-service failure
            if (cachedToken != null && Instant.now().isBefore(tokenExpiry)) {
                log.warn("Service token refresh failed but existing token still valid. " +
                        "Will retry on next request. Error: {}", e.getMessage());
            } else {
                // No valid fallback — this is a hard failure
                log.error("Service token refresh failed and no valid cached token available.", e);
                throw new RuntimeException("Failed to obtain service token from auth-service", e);
            }
        }
    }
}