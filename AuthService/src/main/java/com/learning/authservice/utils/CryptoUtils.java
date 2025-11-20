package com.learning.authservice.utils;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

public class CryptoUtils {
    private static final SecureRandom secureRandom = new SecureRandom();

    // Generate secure random URL-safe base64 string (token raw part)
    public static String generateRandomToken(int bytes) {
        byte[] b = new byte[bytes];
        secureRandom.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // SHA-256 hex
    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    // Create familyId
    public static String newFamilyId() {
        return UUID.randomUUID().toString();
    }

}
