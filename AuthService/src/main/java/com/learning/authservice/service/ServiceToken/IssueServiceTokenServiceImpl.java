package com.learning.authservice.service.ServiceToken;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.learning.authservice.configuration.ServiceRegistryProperties;
import com.learning.authservice.dto.ServiceTokenRequest;
import com.learning.authservice.dto.ServiceTokenResponse;
import com.learning.authservice.service.jwt.JwtService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class IssueServiceTokenServiceImpl implements IssueServiceTokenService {

    private final ServiceRegistryProperties serviceRegistryProperties;
    private final JwtService jwtService;

    @Value("${jwt.service-token-expiry-seconds}")
    private long serviceTokenExpirySeconds;

    @Override
    public ServiceTokenResponse issueServiceToken(ServiceTokenRequest request) {
        String serviceName = request.getServiceName();
        String providedSecret = request.getServiceSecret();

        // Lookup registered secret for this service name
        String registeredSecret = serviceRegistryProperties.getSecrets().get(serviceName);

        // Validate: service must exist AND secret must match
        // Using constant-time comparison to prevent timing attacks
        if (registeredSecret == null || !constantTimeEquals(registeredSecret, providedSecret)) {
            // Generic message — don't reveal whether service name was wrong or secret was
            // wrong
            log.warn("Service token request failed for serviceName: {}", serviceName);
            throw new com.learning.authservice.exception.AuthException(
                    "Invalid service credentials");
        }

        String token = jwtService.generateServiceToken(serviceName);

        log.info("Service token issued for: {}", serviceName);

        return ServiceTokenResponse.builder()
                .token(token)
                .expiresInSeconds(serviceTokenExpirySeconds)
                .serviceName(serviceName)
                .build();
    }

    /**
     * Constant-time string comparison to prevent timing attacks.
     * Regular .equals() short-circuits on first mismatch — an attacker
     * can measure response time to brute-force secrets character by character.
     */
    private boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length())
            return false;
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}