package com.learning.authservice.service.refreshToken;


import com.learning.authservice.exception.ResourceNotFoundException;
import com.learning.authservice.exception.TokenRefreshException;
import com.learning.authservice.utils.CryptoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

/**
 * Manages refresh token family lifecycle in Redis and performs the atomic
 * rotation & validation using a Lua script (CAS).
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisScript<String> refreshTokenRedisScript;
    private final RedisConnectionFactory connectionFactory;

    @Value("${jwt.refresh-idle-expiry-seconds}")
    private long idleExpirySeconds;

    @Value("${jwt.refresh-max-expiry-seconds}")
    private long maxExpirySeconds;

    @Value("${app.refresh-cookie-name:refresh_token}")
    private String cookieName;

    /**
     * Create a new token family (on login). Store current refresh token hash and expiries.
     * Returns raw refresh token (familyId:rawToken) to set in cookie.
     */
    public String createTokenFamily(UUID userId,String role) {
        String familyId = CryptoUtils.newFamilyId();
        String raw = CryptoUtils.generateRandomToken(64); // 512-bit-ish
        String hash = CryptoUtils.sha256Hex(raw);
        long now = Instant.now().getEpochSecond();
        long idleExpiry = now + idleExpirySeconds;
        long maxExpiry = now + maxExpirySeconds;
        String key = redisKey(familyId);

        Map<String, String> map = new HashMap<>();
        map.put("user_id", String.valueOf(userId));
        map.put("role", role);
        map.put("current_refresh_token_hash", hash);
        map.put("idle_expiry", String.valueOf(idleExpiry));
        map.put("max_expiry", String.valueOf(maxExpiry));
        map.put("is_revoked", "0");
        map.put("created_at", String.valueOf(now));
        map.put("last_used_at", String.valueOf(now));

        redisTemplate.opsForHash().putAll(key, map);
        // set TTL to maxExpiry - now so Redis auto cleans after max lifetime
        redisTemplate.expireAt(key, java.util.Date.from(Instant.ofEpochSecond(maxExpiry)));

        // return token that includes family id so lookup is O(1)
        return familyId + ":" + raw;
    }

    /**
     * Atomically validate incoming refresh token and rotate to a new one.
     * Returns new raw refresh token if OK, or throws exception codes to caller.
     */
//    public RotateResult rotateIfValid(String incomingToken) {
//        // incomingToken format: familyId:raw
//        String[] parts = incomingToken.split(":", 2);
//        if (parts.length != 2) {
//            throw new TokenRefreshException("INVALID_TOKEN_FORMAT");
//        }
//        String familyId = parts[0];
//        String raw = parts[1];
//
//        String incomingHash = CryptoUtils.sha256Hex(raw);
//        String newRaw = CryptoUtils.generateRandomToken(64);
//        String newHash = CryptoUtils.sha256Hex(newRaw);
//
//        long now = Instant.now().getEpochSecond();
//        long newIdle = now + idleExpirySeconds;
//
//        String key = redisKey(familyId);
//
//        // call lua script:
//        String result = redisTemplate.execute(refreshTokenRedisScript,
//                List.of(key),
//                incomingHash, newHash, String.valueOf(newIdle), String.valueOf(now));
//
//        if (result == null) {
//            throw new TokenRefreshException("INTERNAL_ERROR");
//        }
//
//        Map<Object, Object> tokenFamilyData = redisTemplate.opsForHash().entries(key);
//        if (tokenFamilyData == null || tokenFamilyData.isEmpty()) {
//            throw new ResourceNotFoundException("Token family not found in Redis");
//        }
//
//        return switch (result) {
//            case "OK" -> // rotation successful — build and return your RotateResult
//                // yield is required when returning objects from switch expressions
//                    new RotateResult(familyId + ":" + newRaw, tokenFamilyData);
//            case "NOT_FOUND" -> throw new TokenRefreshException("FAMILY_NOT_FOUND");
//            case "REVOKED" -> throw new TokenRefreshException("FAMILY_REVOKED");
//            case "MAX_EXPIRED" -> throw new TokenRefreshException("FAMILY_MAX_EXPIRED");
//            case "IDLE_EXPIRED" -> throw new TokenRefreshException("FAMILY_IDLE_EXPIRED");
//            case "HASH_MISMATCH_REVOKED" -> throw new TokenRefreshException("REUSE_DETECTED");
//            default -> throw new TokenRefreshException("UNKNOWN_RESULT: " + result);
//        };
//    }

    public RotateResult rotateIfValid(String incomingToken) {
        String[] parts = incomingToken.split(":", 2);
        if (parts.length != 2) {
            throw new TokenRefreshException("INVALID_TOKEN_FORMAT");
        }

        String familyId = parts[0];
        String raw = parts[1];

        String incomingHash = CryptoUtils.sha256Hex(raw);
        String newRaw = CryptoUtils.generateRandomToken(64);
        String newHash = CryptoUtils.sha256Hex(newRaw);

        long now = Instant.now().getEpochSecond();
        long newIdle = now + idleExpirySeconds;

        String key = redisKey(familyId);

        String result = redisTemplate.execute(
                refreshTokenRedisScript,
                List.of(key),
                incomingHash, newHash, String.valueOf(newIdle), String.valueOf(now)
        );

        if (result == null) {
            throw new TokenRefreshException("INTERNAL_ERROR");
        }

        Map<Object, Object> tokenFamilyData = redisTemplate.opsForHash().entries(key);
        if (tokenFamilyData == null || tokenFamilyData.isEmpty()) {
            throw new ResourceNotFoundException("Token family not found in Redis");
        }

        return switch (result) {
            case "OK" -> new RotateResult(familyId + ":" + newRaw, tokenFamilyData);
            case "NOT_FOUND" -> throw new TokenRefreshException("FAMILY_NOT_FOUND");
            case "REVOKED" -> throw new TokenRefreshException("FAMILY_REVOKED");
            case "MAX_EXPIRED" -> throw new TokenRefreshException("FAMILY_MAX_EXPIRED");
            case "IDLE_EXPIRED" -> throw new TokenRefreshException("FAMILY_IDLE_EXPIRED");
            case "HASH_MISMATCH_REVOKED" -> throw new TokenRefreshException("REUSE_DETECTED");
            default -> throw new TokenRefreshException("UNKNOWN_RESULT: " + result);
        };
    }


    public void revokeFamily(String familyId) {
        String key = redisKey(familyId);
        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForHash().put(key, "is_revoked", "1");
            redisTemplate.delete(key);
        }
    }

    private String redisKey(String familyId) {
        return "token_family:" + familyId;
    }

    // Simple result container
    public record RotateResult(String newRefreshToken, Map<Object, Object> userData) {}
}
