package com.mobisec.in.contentservice.domain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Sent by client after finishing a single-part PUT upload to MinIO.
 * The ETag is in MinIO's response header: ETag: "d41d8cd98f00b204e9800998ecf8427e"
 */
public record UploadCompleteRequest(

        @NotBlank(message = "eTag is required")
        String eTag,

        /**
         * Optional hint from HTMLVideoElement.duration (browser can read this before upload).
         * Stored for display while transcoding is in progress.
         * Transcoding service will overwrite with the authoritative value.
         */
        Integer clientReportedDurationSeconds

) {}
