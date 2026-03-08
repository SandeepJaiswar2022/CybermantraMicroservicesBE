package com.cybermantra.microservices.in.paymentservice.service.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
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
import java.util.Date;

@Service
@Slf4j
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.public-key-path}")
    private Resource publicKeyResource;

    private PublicKey publicKey;

    @PostConstruct
    public void init() {
        try {
            this.publicKey = readPublicKey(publicKeyResource);
            log.info("JWT keys loaded successfully");
        } catch (Exception e) {
            log.error("Failed to load JWT keys", e);
            throw new RuntimeException("Failed to initialize JWT service", e);
        }
    }


//    @Override
//    public String generateAccessToken(UUID userId, String role) {
//        Map<String, Object> claims = new HashMap<>();
//        claims.put("role", role);
//        claims.put("tokenType", "ACCESS");
//
//        return Jwts.builder()
//                .setClaims(claims)
//                .setSubject(userId.toString())  // ✅ Use userId as subject
//                .setIssuer(issuer)
//                .setIssuedAt(new Date(System.currentTimeMillis()))
//                .setExpiration(Date.from(Instant.now().plusSeconds(accessTokenExpirySeconds)))
//                .signWith(privateKey, SignatureAlgorithm.RS256)
//                .compact();
//    }

    /**
     * Validate token signature and expiration
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
     * Extract User ID from token
     */
//    public Long extractUserId(String token) {
//        Claims claims = extractAllClaims(token);
//        String userIdStr = claims.get("userId", String.class);
//        return UUID.fromString(userIdStr);
//    }

//    public Long extractUserId(String token) {
//        Claims claims = extractAllClaims(token);
//        String userIdStr = claims.get("userId", String.class);
//        System.out.println("Extracted userId: {}"+ userIdStr);
//        return Long.parseLong(userIdStr);
//    }


    public String extractRole(String token) {
        Claims claims = extractAllClaims(token);
        return claims.get("role", String.class);
    }
    public String extractSubject(String token)
    {
        Claims claims = extractAllClaims(token);
        return claims.getSubject();
    }
    public boolean isTokenExpired(String token) {
        try {
            Date expiration = extractAllClaims(token).getExpiration();
//            System.out.println(expiration);
            return expiration.before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        }
    }

    /**
     * Extract all claims from token
     */
    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(publicKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    // Helper methods to read keys
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