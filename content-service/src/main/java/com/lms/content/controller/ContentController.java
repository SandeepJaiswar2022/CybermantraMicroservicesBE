package com.lms.content.controller;

import com.lms.content.domain.enums.ContentType;
import com.lms.content.dto.request.ContentUploadRequest;
import com.lms.content.dto.response.ApiResponse;
import com.lms.content.dto.response.ContentItemResponse;
import com.lms.content.dto.response.SignedUrlResponse;
import com.lms.content.security.AuthenticatedUser;
import com.lms.content.service.ContentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Content REST controller.
 *
 * <p>Endpoints:
 * <pre>
 * POST   /content/upload                           Upload a file
 * GET    /content/{contentId}/signed-url           Get signed access URL
 * GET    /content/lecture/{lectureId}              List all content for a lecture
 * GET    /content/instructor                       Paginated instructor content
 * DELETE /content/{contentId}                      Delete content
 * POST   /content/{contentId}/transcode-complete   (Internal) Transcode callback
 * </pre>
 *
 * <p>Authentication: all endpoints require a valid JWT (enforced by Spring Security).
 * Role-based access is enforced at the service layer for fine-grained ownership checks.
 */
@RestController
@RequestMapping("/content")
@RequiredArgsConstructor
@Slf4j
public class ContentController {

    private final ContentService contentService;

    // ─── Upload ───────────────────────────────────────────────────────────

    /**
     * Upload a file (video, resource, or thumbnail) for a lecture.
     *
     * <p>Multipart form data:
     * <pre>
     * lectureId    UUID
     * courseId     UUID
     * contentType  VIDEO | RESOURCE | THUMBNAIL
     * file         binary
     * </pre>
     *
     * <p>The response is returned immediately with status UPLOADING.
     * The client should poll or subscribe to events to know when status is READY.
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ContentItemResponse>> upload(
        @Valid @ModelAttribute ContentUploadRequest request,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID uploaderId = extractUserId(jwt);
        log.info("Upload request from user=[{}], type=[{}]", uploaderId, request.getContentType());

        ContentItemResponse response = contentService.upload(request, uploaderId);
        return ResponseEntity
            .status(HttpStatus.ACCEPTED)  // 202: accepted but not yet complete (async)
            .body(ApiResponse.success("Upload initiated. Content will be available shortly.", response));
    }

    // ─── Signed URL ───────────────────────────────────────────────────────

    /**
     * Generate a time-limited signed URL for accessing content.
     *
     * <p>The URL expires after the configured TTL (default: 1 hour).
     * The client should store the {@code expiresAt} and request a new URL before expiry.
     */
    @GetMapping("/{contentId}/signed-url")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<SignedUrlResponse>> getSignedUrl(
        @PathVariable UUID contentId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID requesterId = extractUserId(jwt);
        boolean isAdmin  = hasRole(jwt, "ADMIN");

        SignedUrlResponse response = contentService.generateSignedUrl(contentId, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─── Queries ──────────────────────────────────────────────────────────

    /**
     * Get all content items for a specific lecture.
     */
    @GetMapping("/lecture/{lectureId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<List<ContentItemResponse>>> getLectureContent(
        @PathVariable UUID lectureId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID requesterId = extractUserId(jwt);
        boolean isAdmin  = hasRole(jwt, "ADMIN");

        List<ContentItemResponse> content =
            contentService.getLectureContent(lectureId, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    /**
     * Paginated list of content for the authenticated instructor.
     */
    @GetMapping("/instructor")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<ContentItemResponse>>> getInstructorContent(
        @RequestParam(defaultValue = "VIDEO") ContentType contentType,
        @PageableDefault(size = 20, sort = "createdAt") Pageable pageable,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID instructorId = extractUserId(jwt);
        Page<ContentItemResponse> page =
            contentService.getInstructorContent(instructorId, contentType, pageable);
        return ResponseEntity.ok(ApiResponse.success(page));
    }

    // ─── Delete ───────────────────────────────────────────────────────────

    @DeleteMapping("/{contentId}")
    @PreAuthorize("hasAnyRole('INSTRUCTOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable UUID contentId,
        @AuthenticationPrincipal Jwt jwt
    ) {
        UUID requesterId = extractUserId(jwt);
        boolean isAdmin  = hasRole(jwt, "ADMIN");

        contentService.delete(contentId, requesterId, isAdmin);
        return ResponseEntity.ok(ApiResponse.success("Content deleted successfully.", null));
    }

    // ─── Internal: Transcoding Callback ───────────────────────────────────

    /**
     * Called by the internal transcoding service when HLS encoding is complete.
     * This endpoint should be restricted to internal network / service account only
     * (enforce via API Gateway IP allowlist or a service-to-service token).
     */
    @PostMapping("/{contentId}/transcode-complete")
    @PreAuthorize("hasRole('SERVICE')")
    public ResponseEntity<ApiResponse<Void>> transcodeComplete(
        @PathVariable UUID contentId,
        @RequestParam String hlsManifestKey
    ) {
        log.info("Transcode complete callback: contentId=[{}], hlsKey=[{}]", contentId, hlsManifestKey);
        contentService.onTranscodeComplete(contentId, hlsManifestKey);
        return ResponseEntity.ok(ApiResponse.success("Transcode acknowledged.", null));
    }

    // ─── JWT helpers ──────────────────────────────────────────────────────

    private UUID extractUserId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private boolean hasRole(Jwt jwt, String role) {
        List<String> roles = jwt.getClaimAsStringList("roles");
        return roles != null && roles.contains(role);
    }
}
