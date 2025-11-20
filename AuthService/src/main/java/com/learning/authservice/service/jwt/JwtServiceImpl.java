package com.learning.authservice.service.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.core.userdetails.UserDetails;
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
import java.util.function.Function;

@Service
public class JwtServiceImpl implements JwtService {

    @Value("${jwt.private-key-path}")
    private Resource privateKeyResource;

    @Value("${jwt.public-key-path}")
    private Resource publicKeyResource;

    @Value("${jwt.access-token-expiry-seconds}")
    private long accessTokenExpirySeconds;

    private PrivateKey privateKey;
    private PublicKey publicKey;

    @PostConstruct
    public void init() throws Exception {
        this.privateKey = readPrivateKey(privateKeyResource);
        this.publicKey = readPublicKey(publicKeyResource);
    }

    //***************************

    // 👤 Extract username (subject)
    @Override
    public String extractUsername(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    // 🧠 Extract any claim

    public <T> T extractClaim(String token, Function<Claims,T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    // 🛠️ Extract all claims safely
    private Claims extractAllClaims(String token) {
        try {
            return Jwts
                    .parserBuilder()
                    .setSigningKey(publicKey)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

        } catch (Exception e) {
            // System.out.println("\n\n\nExpired\n\n");
            throw e;
        }

    }

    // ⌛ Extract expiration
    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    // 🚫 Check if token is expired
    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    // ✅ Validate token
    @Override
    public boolean isTokenValid(String token, UUID userId) {
        final String claimsUserId = extractUsername(token);
        return (claimsUserId.equals(userId.toString()) && !isTokenExpired(token));
    }

    // 🎟️ Generate Access Token (valid for 15 mins)
    public String generateAccessToken(UUID userId) {

        return buildToken(new HashMap<>(), userId.toString());
    }


    // 🧱 Common Token Builder
    private String buildToken(
            Map<String, Object> extraClaims,
            String userId
    ) {
        return Jwts.builder()
                .setClaims(extraClaims)
                .setSubject(userId)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(Date.from(Instant.now().plusSeconds(accessTokenExpirySeconds)))
                .signWith(privateKey, SignatureAlgorithm.RS256)
                .compact();
    }
    //***************************


    // helpers to read keys (PEM PKCS#8 for private, X.509 for public)
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
