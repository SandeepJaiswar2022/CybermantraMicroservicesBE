package com.learning.authservice.service.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
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

    @Value("${jwt.public-key-path}")
    private Resource publicKeyResource;

    @Value("${jwt.access-token-expiry-seconds}")
    private long accessTokenExpirySeconds;

    @Value("${jwt.service-token-expiry-seconds}")
    private long serviceTokenExpirySeconds;

    @Value("${jwt.issuer:auth-service}")
    private String issuer;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.privateKey = readPrivateKey(privateKeyResource);
            this.publicKey = readPublicKey(publicKeyResource);
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

    /**
     * Validate token — checks signature and expiration.
     */

    public boolean isTokenValid(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (ExpiredJwtException e) {
            log.debug("Token expired: {}", e.getMessage());
            return false;
        } catch (SignatureException e) {
            log.error("Invalid token signature: {}", e.getMessage());
            return false;
        } catch (MalformedJwtException e) {
            log.error("Malformed token: {}", e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("Token validation error: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Extract userId from ACCESS token subject.
     * FIXED: was incorrectly reading from custom claim "userId" — now reads from
     * subject.
     */

    public UUID extractUserId(String token) {
        Claims claims = extractAllClaims(token);
        String subject = claims.getSubject();

        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Subject not found in token");
        }

        try {
            return UUID.fromString(subject);
        } catch (IllegalArgumentException e) {
            // Subject is not a UUID — this is likely a service token, not a user token
            throw new IllegalArgumentException(
                    "Token subject is not a valid userId. " +
                            "Ensure you are using a user ACCESS token, not a service token.");
        }
    }

    public String extractEmail(String token) {
        return extractAllClaims(token).get("email", String.class);
    }

    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        String role = claims.get("role", String.class);

        if (role == null || role.isBlank()) {
            throw new IllegalArgumentException("Role claim not found in token");
        }

        return role;
    }

    public boolean isTokenExpired(String token) {
        try {
            return extractAllClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
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

    private static PublicKey readPublicKey(Resource res) throws Exception {
        try (InputStream is = res.getInputStream()) {
            byte[] bytes = is.readAllBytes();
            String pem = new String(bytes)
                    .replaceAll("-----BEGIN (.*)-----", "")
                    .replaceAll("-----END (.*)-----", "")
                    .replaceAll("\\s", "");
            byte[] decoded = java.util.Base64.getDecoder().decode(pem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return kf.generatePublic(spec);
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