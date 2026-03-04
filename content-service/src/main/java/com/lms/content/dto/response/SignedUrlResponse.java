package com.lms.content.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.UUID;

/** Response containing a time-limited signed URL for content access. */
@Data
@Builder
public class SignedUrlResponse {

    private UUID contentId;
    /** The signed URL to access the content. Valid until expiresAt. */
    private String signedUrl;
    /** Unix epoch seconds when the URL expires. Client should refresh before this. */
    private long expiresAt;
    /** Seconds remaining until expiry (for convenience). */
    private long ttlSeconds;
}
