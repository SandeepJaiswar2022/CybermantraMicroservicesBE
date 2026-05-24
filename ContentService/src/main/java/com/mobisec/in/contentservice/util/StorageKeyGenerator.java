package com.mobisec.in.contentservice.util;

import com.mobisec.in.contentservice.domain.enums.ContentType;

import java.util.UUID;

/**
 * Generates deterministic, UUID-based storage keys.
 *
 * Format: lectures/{lectureId}/{contentType}/{randomUUID}.{ext}
 * Example: lectures/550e8400/VIDEO/def45678.mp4
 *
 * Why UUID-based (not original filename):
 * - Prevents path traversal attacks — no user input in the path
 * - No naming conflicts — UUIDs are unique per upload
 * - Does not leak original filename to storage layer
 */
public class StorageKeyGenerator {

    private StorageKeyGenerator() {}

    public static String generate(UUID lectureId, ContentType contentType, String originalFilename) {
        String extension = extractExtension(originalFilename);
        return String.format("lectures/%s/%s/%s%s",
                lectureId, contentType.name(), UUID.randomUUID(), extension);
    }

    private static String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        if (dot > 0 && dot < filename.length() - 1) {
            return "." + filename.substring(dot + 1).toLowerCase();
        }
        return "";
    }
}
