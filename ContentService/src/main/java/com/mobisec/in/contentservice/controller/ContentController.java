package com.mobisec.in.contentservice.controller;

import com.mobisec.in.contentservice.domain.dto.*;
import com.mobisec.in.contentservice.service.ContentService;
import com.mobisec.in.contentservice.service.SseEmitterService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * Content controller — follows the exact same pattern as CourseController.
 *
 * Role checked via @PreAuthorize("hasRole('INSTRUCTOR')").
 * userId extracted from request attribute set by JwtAuthenticationFilter.
 * userRole extracted from request attribute for ownership logic.
 *
 * All upload endpoints are INSTRUCTOR-only.
 * Status read endpoint is accessible to any authenticated user (students need it).
 */
@RestController
@RequestMapping("/api/v1/content")
@RequiredArgsConstructor
@Slf4j
public class ContentController {

    private final ContentService    contentService;
    private final SseEmitterService sseEmitterService;

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/content/upload/initiate
    //
    // Step 1 — instructor sends file metadata.
    // Response is either UploadInitiateResponse (small file) or
    // MultipartUploadInitiateResponse (large file >= 100MB).
    // The client inspects the "multipart" boolean field to know which flow.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/upload/initiate")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<Object>> initiateUpload(
            @Valid @RequestBody UploadInitiateRequest request,
            HttpServletRequest httpRequest) {

        UUID   userId   = (UUID)   httpRequest.getAttribute("userId");
        String userRole = (String) httpRequest.getAttribute("userRole");

        log.info("POST /api/v1/content/upload/initiate — instructor={}, type={}, size={}",
                userId, request.contentType(), request.fileSizeBytes());

        Object response = contentService.initiateUpload(request, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Upload initiated successfully", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/content/{contentId}/complete
    //
    // Step 3 for single-part uploads (Step 2 was direct PUT to MinIO).
    // Client sends the ETag from MinIO's response header.
    // Server verifies file exists, transitions to PROCESSING, publishes event.
    //
    // Idempotent: if already PROCESSING or READY, returns current status.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/{contentId}/complete")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ContentStatusResponse>> completeUpload(
            @PathVariable UUID contentId,
            @Valid @RequestBody UploadCompleteRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");

        log.info("POST /api/v1/content/{}/complete — instructor={}", contentId, userId);

        try {
            ContentStatusResponse response = contentService.completeUpload(contentId, request, userId);
            return ResponseEntity.ok(ApiResponse.success("Upload completed successfully", response));
        } catch (ContentService.AlreadyCompletedException e) {
            // Duplicate call — idempotent, return current status
            ContentStatusResponse current = contentService.getStatus(contentId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Upload already completed. Current status: " + e.getCurrentStatus(), current));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/v1/content/{contentId}/complete-multipart
    //
    // Step 3 for multipart uploads.
    // Client sends the list of {partNumber, eTag} pairs collected from MinIO.
    // Server calls MinIO to assemble parts, then follows same flow as single-part.
    //
    // Idempotent: if already PROCESSING or READY, returns current status.
    // ─────────────────────────────────────────────────────────────────────────
    @PostMapping("/{contentId}/complete-multipart")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    public ResponseEntity<ApiResponse<ContentStatusResponse>> completeMultipartUpload(
            @PathVariable UUID contentId,
            @Valid @RequestBody MultipartCompleteRequest request,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");

        log.info("POST /api/v1/content/{}/complete-multipart — instructor={}, parts={}",
                contentId, userId, request.parts().size());

        try {
            ContentStatusResponse response =
                    contentService.completeMultipartUpload(contentId, request, userId);
            return ResponseEntity.ok(
                    ApiResponse.success("Multipart upload completed successfully", response));
        } catch (ContentService.AlreadyCompletedException e) {
            ContentStatusResponse current = contentService.getStatus(contentId);
            return ResponseEntity.ok(ApiResponse.success(
                    "Upload already completed. Current status: " + e.getCurrentStatus(), current));
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/content/{contentId}/status
    //
    // Returns the current ContentItem status and metadata.
    // Accessible to any authenticated user — students need to check if a
    // lecture's content is READY before attempting playback.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{contentId}/status")
    public ResponseEntity<ApiResponse<ContentStatusResponse>> getStatus(
            @PathVariable UUID contentId) {

        log.debug("GET /api/v1/content/{}/status", contentId);
        ContentStatusResponse response = contentService.getStatus(contentId);
        return ResponseEntity.ok(ApiResponse.success("Status retrieved", response));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/content/{contentId}/status/stream
    //
    // Server-Sent Events stream for real-time status updates.
    // The browser's EventSource API keeps this connection open.
    // The server pushes "status" events whenever the ContentItem status changes.
    //
    // Usage (browser):
    //   const es = new EventSource('/api/v1/content/{id}/status/stream', {
    //     headers: { Authorization: 'Bearer <token>' }
    //   });
    //   es.addEventListener('status', e => {
    //     const data = JSON.parse(e.data);
    //     if (data.status === 'READY' || data.status === 'FAILED') es.close();
    //   });
    //
    // Cross-pod delivery via Redis pub/sub — see SseEmitterService.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping(value = "/{contentId}/status/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamStatus(@PathVariable UUID contentId) {

        log.info("GET /api/v1/content/{}/status/stream — SSE connection opened", contentId);

        // Verify content exists before opening the stream
        ContentStatusResponse current = contentService.getStatus(contentId);

        SseEmitter emitter = sseEmitterService.createEmitter(contentId);

        // Push current status immediately as the first event.
        // This ensures the client gets a state even if they connect after upload.
        try {
            emitter.send(SseEmitter.event()
                    .name("status")
                    .data(current));
        } catch (Exception e) {
            log.warn("Failed to send initial status on SSE open: contentId={}", contentId);
        }

        return emitter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/v1/content/{contentId}/download-url
    //
    // Returns a short-lived presigned GET URL for accessing the content.
    // Only available when status=READY.
    // storageKey is NEVER exposed — only a signed URL with 60-min expiry.
    // ─────────────────────────────────────────────────────────────────────────
    @GetMapping("/{contentId}/download-url")
    public ResponseEntity<ApiResponse<String>> getDownloadUrl(
            @PathVariable UUID contentId) {

        log.debug("GET /api/v1/content/{}/download-url", contentId);
        String url = contentService.getPresignedDownloadUrl(contentId);
        return ResponseEntity.ok(ApiResponse.success("Download URL generated", url));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DELETE /api/v1/content/{contentId}
    //
    // Soft-delete — sets status=DELETED, publishes ContentDeletedEvent via outbox.
    // MinIO object is cleaned up asynchronously by the cleanup scheduler.
    // Only the owning INSTRUCTOR can delete.
    // ─────────────────────────────────────────────────────────────────────────
    @DeleteMapping("/{contentId}")
    @PreAuthorize("hasRole('INSTRUCTOR')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public ResponseEntity<ApiResponse<Void>> deleteContent(
            @PathVariable UUID contentId,
            HttpServletRequest httpRequest) {

        UUID userId = (UUID) httpRequest.getAttribute("userId");

        log.info("DELETE /api/v1/content/{} — instructor={}", contentId, userId);

        contentService.deleteContent(contentId, userId);
        return ResponseEntity.status(HttpStatus.NO_CONTENT)
                .body(ApiResponse.success("Content deleted successfully",null));
    }
}
