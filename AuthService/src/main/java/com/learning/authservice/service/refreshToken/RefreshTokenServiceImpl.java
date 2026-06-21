package com.learning.authservice.service.refreshToken;

import com.learning.authservice.dto.UserDto;
import com.learning.authservice.exception.ResourceNotFoundException;
import com.learning.authservice.exception.TokenRefreshException;
import com.learning.authservice.utils.CryptoUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Date;
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

    @Value("${jwt.refresh-idle-expiry-seconds}")
    private long idleExpirySeconds;

    @Value("${jwt.refresh-max-expiry-seconds}")
    private long maxExpirySeconds;

    /**
     * Creates a new token family in Redis on login.
     * Stores full user snapshot so refresh never needs a DB call.
     * Returns raw token in format: familyId:rawToken
     */
    public String createTokenFamily(UUID userId, String role, String email,
            String firstName, String lastName, boolean isEmailVerified) {

        String familyId = CryptoUtils.newFamilyId();
        String raw = CryptoUtils.generateRandomToken(64);
        String hash = CryptoUtils.sha256Hex(raw);
        long now = Instant.now().getEpochSecond();

        Map<String, String> familyData = new HashMap<>();
        familyData.put("user_id", userId.toString());
        familyData.put("role", role);
        familyData.put("email", email);
        familyData.put("first_name", firstName);
        familyData.put("last_name", lastName);
        familyData.put("is_email_verified", String.valueOf(isEmailVerified));
        familyData.put("current_refresh_token_hash", hash);
        familyData.put("idle_expiry", String.valueOf(now + idleExpirySeconds));
        familyData.put("max_expiry", String.valueOf(now + maxExpirySeconds));
        familyData.put("is_revoked", "0");
        familyData.put("created_at", String.valueOf(now));
        familyData.put("last_used_at", String.valueOf(now));

        redisTemplate.opsForHash().putAll(redisKey(familyId), familyData);
        redisTemplate.expireAt(
                redisKey(familyId),
                Date.from(Instant.ofEpochSecond(now + maxExpirySeconds)));

        return familyId + ":" + raw;
    }

    /**
     * Atomically validates and rotates the refresh token via Lua CAS.
     * Returns new raw token + user snapshot from Redis — zero DB call.
     */
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

        String key = redisKey(familyId);
        String result = redisTemplate.execute(
                refreshTokenRedisScript,
                List.of(key),
                incomingHash, newHash, String.valueOf(now + idleExpirySeconds), String.valueOf(now));

        if (result == null)
            throw new TokenRefreshException("INTERNAL_ERROR");

        return switch (result) {
            case "OK" -> {
                Map<Object, Object> data = redisTemplate.opsForHash().entries(key);
                if (data == null || data.isEmpty()) {
                    throw new ResourceNotFoundException("Token family not found in Redis");
                }
                yield new RotateResult(familyId + ":" + newRaw, buildUserDto(data));
            }
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

    /**
     * Builds UserDto from Redis hash — no DB call needed.
     */
    private UserDto buildUserDto(Map<Object, Object> data) {
        return UserDto.builder()
                .id(UUID.fromString((String) data.get("user_id")))
                .email((String) data.get("email"))
                .firstName((String) data.get("first_name"))
                .lastName((String) data.get("last_name"))
                .role((String) data.get("role"))
                .isEmailVerified(Boolean.parseBoolean((String) data.get("is_email_verified")))
                .build();
    }

    private String redisKey(String familyId) {
        return "token_family:" + familyId;
    }

    
}
