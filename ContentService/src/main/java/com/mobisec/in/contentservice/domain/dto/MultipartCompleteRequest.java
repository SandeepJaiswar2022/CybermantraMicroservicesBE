package com.mobisec.in.contentservice.domain.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

/**
 * Sent by client after all parts have been PUT to MinIO.
 * The client must collect each part's ETag from MinIO response headers.
 * MinIO uses these ETags to verify and assemble the final object.
 */
public record MultipartCompleteRequest(

        @NotBlank(message = "uploadId is required")
        String uploadId,

        @NotEmpty(message = "parts list cannot be empty")
        @Valid
        List<CompletedPart> parts,

        Integer clientReportedDurationSeconds

) {
    public record CompletedPart(
            @NotNull @Positive Integer partNumber,
            @NotBlank String eTag
    ) {}
}
