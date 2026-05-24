package com.mobisec.in.contentservice.domain.dto;

import com.mobisec.in.contentservice.domain.enums.ContentType;
import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * Step 1 — instructor sends file metadata (NOT bytes).
 * Server decides single-part vs multipart based on fileSizeBytes.
 */
public record UploadInitiateRequest(

        @NotNull(message = "lectureId is required")
        UUID lectureId,

        @NotNull(message = "courseId is required")
        UUID courseId,

        @NotNull(message = "contentType is required")
        ContentType contentType,

        @NotBlank(message = "originalFilename is required")
        @Size(max = 255, message = "filename must not exceed 255 characters")
        String originalFilename,

        /**
         * Client-declared MIME type — validated here but re-checked with
         * Apache Tika after upload completes. Never trust for security decisions.
         */
        @NotBlank(message = "mimeType is required")
        @Size(max = 100)
        String mimeType,

        @NotNull(message = "fileSizeBytes is required")
        @Min(value = 1, message = "file cannot be empty")
        @Max(value = 5_368_709_120L, message = "file exceeds 5 GB limit")
        Long fileSizeBytes

) {}
