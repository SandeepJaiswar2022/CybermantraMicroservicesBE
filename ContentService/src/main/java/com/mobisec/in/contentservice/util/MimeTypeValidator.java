package com.mobisec.in.contentservice.util;

import com.mobisec.in.contentservice.domain.enums.ContentType;
import com.mobisec.in.contentservice.exception.InvalidUploadException;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Validates that the client-declared MIME type is acceptable for the
 * given ContentType. This is a pre-upload gate — we reject obviously
 * wrong types before issuing a presigned URL.
 *
 * Note: This validates the CLIENT-DECLARED type only. Apache Tika
 * re-checks the real type from the file's magic bytes after upload.
 */
@Component
public class MimeTypeValidator {

    private static final Set<String> VIDEO_TYPES = Set.of(
            "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    );

    private static final Set<String> RESOURCE_TYPES = Set.of(
            "application/pdf",
            "application/zip",
            "application/x-zip-compressed",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation"
    );

    private static final Set<String> THUMBNAIL_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif"
    );

    public void validate(String mimeType, ContentType contentType) {
        boolean valid = switch (contentType) {
            case VIDEO     -> VIDEO_TYPES.contains(mimeType);
            case RESOURCE  -> RESOURCE_TYPES.contains(mimeType);
            case THUMBNAIL -> THUMBNAIL_TYPES.contains(mimeType);
        };

        if (!valid) {
            throw new InvalidUploadException(
                    String.format("MIME type '%s' is not permitted for content type %s. " +
                                  "Allowed: %s", mimeType, contentType, allowedFor(contentType)));
        }
    }

    private Set<String> allowedFor(ContentType contentType) {
        return switch (contentType) {
            case VIDEO     -> VIDEO_TYPES;
            case RESOURCE  -> RESOURCE_TYPES;
            case THUMBNAIL -> THUMBNAIL_TYPES;
        };
    }
}
