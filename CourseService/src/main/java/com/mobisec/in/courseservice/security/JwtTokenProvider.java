package com.mobisec.in.courseservice.security;


import com.mobisec.in.courseservice.exception.JwtAuthenticationException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.util.Date;
import java.util.UUID;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${jwt.public-key-path}")
    private Resource publicKeyResource;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.publicKey = readPublicKey(publicKeyResource);
            log.info("JWT public key loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load JWT public key", e);
            throw new RuntimeException("Failed to initialize JWT token provider", e);
        }
    }

    /**
     * Validate JWT token signature and expiration
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token);

            log.debug("Token validated successfully");
            return true;

        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired: {}", e.getMessage());
            throw new JwtAuthenticationException("Token has expired");
        } catch (SignatureException e) {
            log.error("Invalid JWT signature: {}", e.getMessage());
            throw new JwtAuthenticationException("Invalid token signature");
        } catch (MalformedJwtException e) {
            log.error("Malformed JWT token: {}", e.getMessage());
            throw new JwtAuthenticationException("Malformed token");
        } catch (Exception e) {
            log.error("JWT validation error: {}", e.getMessage());
            throw new JwtAuthenticationException("Token validation failed");
        }
    }

    /**
     * Extract all claims from token
     */
    public Claims extractClaims(String token) {
        try {
            return Jwts.parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.error("Failed to extract claims from token", e);
            throw new JwtAuthenticationException("Failed to extract token claims");
        }
    }

    /**
     * Extract User ID from token
     */
    public UUID extractUserId(String token) {
        Claims claims = extractClaims(token);
        String userIdStr = claims.get("userId", String.class);

        if (userIdStr == null || userIdStr.isBlank()) {
            throw new JwtAuthenticationException("User ID not found in token");
        }

        try {
            return UUID.fromString(userIdStr);
        } catch (IllegalArgumentException e) {
            throw new JwtAuthenticationException("Invalid User ID format in token");
        }
    }



    /**
     * Extract Role from token
     */
    public String extractRole(String token) {
        Claims claims = extractClaims(token);
        String role = claims.get("role", String.class);

        if (role == null || role.isBlank()) {
            throw new JwtAuthenticationException("Role not found in token");
        }

        return role;
    }

    /**
     * Check if token is expired
     */
    public boolean isTokenExpired(String token) {
        try {
            Claims claims = extractClaims(token);
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Extract subject (email) from token
     */
    public String extractSubject(String token) {
        Claims claims = extractClaims(token);
        return claims.getSubject();
    }

    /**
     * Read public key from file
     */
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
