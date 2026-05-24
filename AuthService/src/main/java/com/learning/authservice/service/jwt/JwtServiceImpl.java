package com.learning.authservice.service.jwt;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.private-key-path}")
    private Resource privateKeyResource;


    @Value("${jwt.access-token-expiry-seconds}")
    private long accessTokenExpirySeconds;

    @Value("${jwt.service-token-expiry-seconds}")
    private long serviceTokenExpirySeconds;

    @Value("${jwt.issuer:auth-service}")
    private String issuer;

    private PrivateKey privateKey;

    @PostConstruct
    public void init() {
        try {
            this.privateKey = readPrivateKey(privateKeyResource);
            log.info("JWT keys loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load JWT keys", e);
            throw new RuntimeException("Failed to initialize JWT service", e);
        }
    }

    /**
     * Generate Access Token for authenticated users.
     * Subject = userId (UUID string)
     * Claims: role, tokenType = ACCESS
     */
    @Override
    public String generateAccessToken(UUID userId, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("tokenType", "ACCESS");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userId.toString()) // subject = userId
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(accessTokenExpirySeconds)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    /**
     * Generate Service Token for internal service-to-service calls.
     * Subject = serviceName (e.g. "course-service")
     * Claims: role = SERVICE, tokenType = SERVICE
     * No userId — this represents a machine identity, not a human.
     */
    @Override
    public String generateServiceToken(String serviceName) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", "SERVICE");
        claims.put("tokenType", "SERVICE");

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(serviceName) // subject = service name
                .setIssuer(issuer)
                .setIssuedAt(new Date())
                .setExpiration(Date.from(Instant.now().plusSeconds(serviceTokenExpirySeconds)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }

    private static PrivateKey readPrivateKey(Resource res) throws Exception {
        try (InputStream is = res.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            String pem = new String(bytes)
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(pem);
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePrivate(spec);
        }
    }
}
/*
 * 
 * ---
 * 
 * ## Complete Course Service Security Implementation
 * 
 * ### **Step 1: Project Structure**
 * ```
 * course-service/
 * ├── src/main/java/com/courseservice/
 * │ ├── config/
 * │ │ └── SecurityConfig.java
 * │ ├── security/
 * │ │ ├── JwtTokenProvider.java
 * │ │ ├── JwtAuthenticationFilter.java
 * │ │ └── SecurityUtils.java
 * │ ├── exception/
 * │ │ ├── JwtAuthenticationException.java
 * │ │ └── GlobalExceptionHandler.java (update)
 * │ └── controller/
 * │ └── CategoryController.java (update)
 * └── src/main/resources/
 * ├── application.yml
 * └── keys/
 * └── public_key.pem (copy from auth-service)
 * 
 * 
 */