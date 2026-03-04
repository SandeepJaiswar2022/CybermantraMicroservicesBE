package com.lms.content.util;

import com.lms.content.domain.enums.ContentType;
import com.lms.content.exception.InvalidFileException;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Set;

/**
 * File validation utilities.
 *
 * <p><b>Why Apache Tika for MIME detection?</b>
 * Clients can trivially forge the {@code Content-Type} header. Tika reads
 * the actual file magic bytes (first N bytes of the file) to determine the
 * true MIME type. This prevents disguising a malicious executable as a PDF.
 *
 * <p>Tika is used only for detection — actual upload bytes are streamed
 * from the MultipartFile, which may have been spooled to disk if >10MB.
 */
@Slf4j
public final class FileValidationUtil {

    private static final Tika TIKA = new Tika();

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
        "video/mp4", "video/webm", "video/quicktime", "video/x-msvideo"
    );
    private static final Set<String> ALLOWED_RESOURCE_TYPES = Set.of(
        "application/pdf", "application/zip",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );
    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/webp"
    );

    private static final long MAX_VIDEO_BYTES    = 5L * 1024 * 1024 * 1024; // 5 GB
    private static final long MAX_RESOURCE_BYTES = 100L * 1024 * 1024;       // 100 MB
    private static final long MAX_IMAGE_BYTES    = 10L * 1024 * 1024;        // 10 MB

    private FileValidationUtil() {}

    /**
     * Validates file MIME type and size against allowed rules for the content type.
     *
     * @return the detected MIME type (trusting Tika, not client header)
     */
    public static String validateAndDetectMime(MultipartFile file, ContentType contentType) {
        if (file == null || file.isEmpty()) {
            throw new InvalidFileException("File is empty or not provided.");
        }

        // Detect MIME from bytes (not from client-supplied Content-Type)
        String detectedMime;
        try {
            detectedMime = TIKA.detect(file.getBytes());
        } catch (IOException e) {
            log.error("Failed to detect MIME type for file [{}]", file.getOriginalFilename(), e);
            throw new InvalidFileException("Could not read uploaded file.");
        }

        log.debug("Detected MIME=[{}] for file=[{}]", detectedMime, file.getOriginalFilename());

        switch (contentType) {
            case VIDEO -> {
                if (!ALLOWED_VIDEO_TYPES.contains(detectedMime)) {
                    throw new InvalidFileException(
                        "Invalid video format: " + detectedMime + ". Allowed: mp4, webm, mov.");
                }
                if (file.getSize() > MAX_VIDEO_BYTES) {
                    throw new InvalidFileException("Video exceeds 5 GB limit.");
                }
            }
            case RESOURCE -> {
                if (!ALLOWED_RESOURCE_TYPES.contains(detectedMime)) {
                    throw new InvalidFileException(
                        "Invalid resource format: " + detectedMime + ". Allowed: pdf, zip, docx.");
                }
                if (file.getSize() > MAX_RESOURCE_BYTES) {
                    throw new InvalidFileException("Resource file exceeds 100 MB limit.");
                }
            }
            case THUMBNAIL -> {
                if (!ALLOWED_IMAGE_TYPES.contains(detectedMime)) {
                    throw new InvalidFileException(
                        "Invalid image format: " + detectedMime + ". Allowed: jpeg, png, webp.");
                }
                if (file.getSize() > MAX_IMAGE_BYTES) {
                    throw new InvalidFileException("Thumbnail exceeds 10 MB limit.");
                }
            }
        }
        return detectedMime;
    }

    /**
     * Returns the storage resource type string expected by Cloudinary and used
     * to resolve MinIO bucket.
     */
    public static String resourceTypeFor(ContentType contentType) {
        return switch (contentType) {
            case VIDEO     -> "video";
            case THUMBNAIL -> "image";
            case RESOURCE  -> "raw";
        };
    }

    /**
     * Returns the storage folder prefix for organizing files.
     */
    public static String folderFor(ContentType contentType) {
        return switch (contentType) {
            case VIDEO     -> "videos";
            case THUMBNAIL -> "thumbnails";
            case RESOURCE  -> "resources";
        };
    }
}
