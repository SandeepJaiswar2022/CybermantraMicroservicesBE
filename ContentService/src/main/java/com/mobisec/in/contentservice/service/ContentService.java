package com.mobisec.in.contentservice.service;

import com.mobisec.in.contentservice.config.RabbitMQConfig;
import com.mobisec.in.contentservice.domain.dto.*;
import com.mobisec.in.contentservice.domain.entity.ContentItem;
import com.mobisec.in.contentservice.domain.enums.ContentStatus;
import com.mobisec.in.contentservice.domain.enums.ContentType;
import com.mobisec.in.contentservice.domain.enums.StorageProvider;
import com.mobisec.in.contentservice.event.ContentDeletedEvent;
import com.mobisec.in.contentservice.event.ContentUploadedEvent;
import com.mobisec.in.contentservice.event.outbox.OutboxEventService;
import com.mobisec.in.contentservice.exception.ForbiddenException;
import com.mobisec.in.contentservice.exception.InvalidUploadException;
import com.mobisec.in.contentservice.exception.ResourceNotFoundException;
import com.mobisec.in.contentservice.mapper.ContentMapper;
import com.mobisec.in.contentservice.repository.ContentItemRepository;
import com.mobisec.in.contentservice.storage.StorageService;
import com.mobisec.in.contentservice.util.MimeTypeValidator;
import com.mobisec.in.contentservice.util.StorageKeyGenerator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContentService {

    private final ContentItemRepository contentItemRepository;
    private final StorageService        storageService;
    private final OutboxEventService    outboxEventService;
    private final SseEmitterService     sseEmitterService;
    private final ContentMapper         contentMapper;
    private final MimeTypeValidator     mimeTypeValidator;

    @Value("${minio.multipart.threshold-bytes:104857600}")
    private long multipartThresholdBytes;

    @Value("${minio.multipart.part-size-bytes:10485760}")
    private long partSizeBytes;

    @Value("${minio.presigned-expiry.video:720}")
    private int videoExpiryMinutes;

    @Value("${minio.presigned-expiry.resource:120}")
    private int resourceExpiryMinutes;

    @Value("${minio.presigned-expiry.thumbnail:30}")
    private int thumbnailExpiryMinutes;

    @Value("${content.upload.max-size-bytes.video:5368709120}")
    private long maxVideoBytes;

    @Value("${content.upload.max-size-bytes.resource:524288000}")
    private long maxResourceBytes;

    @Value("${content.upload.max-size-bytes.thumbnail:10485760}")
    private long maxThumbnailBytes;

    @Value("${content.cleanup.abandoned-threshold-hours:6}")
    private int abandonedThresholdHours;

    // ─────────────────────────────────────────────────────────────────────────
    // initiateUpload
    //
    // Step 1 of the upload flow. The instructor sends file metadata.
    // We decide single-part vs multipart based on file size, then return
    // either UploadInitiateResponse or MultipartUploadInitiateResponse.
    //
    // The ContentItem is created here with status=UPLOADING so we have a
    // tracking record even before the file arrives in MinIO.
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public Object initiateUpload(UploadInitiateRequest request, UUID instructorId) {

        log.info("Upload initiate: instructor={}, type={}, size={}, file={}",
                instructorId, request.contentType(), request.fileSizeBytes(),
                request.originalFilename());

        // 1. Validate MIME type is permitted for this content type
        mimeTypeValidator.validate(request.mimeType(), request.contentType());

        // 2. Validate file size is within the limit for this content type
        validateFileSize(request.fileSizeBytes(), request.contentType());

        // 3. Generate a UUID-based storage key — never uses original filename
        String storageKey = StorageKeyGenerator.generate(
                request.lectureId(), request.contentType(), request.originalFilename());

        // 4. Determine presigned URL expiry based on content type
        Duration expiry = expiryFor(request.contentType());
        Instant expiresAt = Instant.now().plus(expiry);

        // 5. Decide: single-part or multipart upload?
        boolean useMultipart = request.fileSizeBytes() >= multipartThresholdBytes;

        if (useMultipart) {
            return initiateMultipartUpload(request, instructorId, storageKey, expiry, expiresAt);
        } else {
            return initiateSinglePartUpload(request, instructorId, storageKey, expiry, expiresAt);
        }
    }

    private UploadInitiateResponse initiateSinglePartUpload(
            UploadInitiateRequest request, UUID instructorId,
            String storageKey, Duration expiry, Instant expiresAt) {

        // Persist ContentItem with status=UPLOADING (no minioUploadId for single-part)
        ContentItem item = buildContentItem(request, instructorId, storageKey, null);
        item = contentItemRepository.save(item);

        // Generate the presigned PUT URL — client uploads directly to MinIO
        String presignedUrl = storageService.generatePresignedUploadUrl(
                storageKey, request.mimeType(), expiry);

        log.info("Single-part upload initiated: contentId={}, key={}", item.getId(), storageKey);

        return new UploadInitiateResponse(
                item.getId(), presignedUrl, expiresAt, item.getStatus().name(), false);
    }

    private MultipartUploadInitiateResponse initiateMultipartUpload(
            UploadInitiateRequest request, UUID instructorId,
            String storageKey, Duration expiry, Instant expiresAt) {

        // Tell MinIO we are starting a multipart upload — get uploadId back
        String uploadId = storageService.initiateMultipartUpload(storageKey, request.mimeType());

        // Persist ContentItem — store uploadId so /complete-multipart can reference it
        ContentItem item = buildContentItem(request, instructorId, storageKey, uploadId);
        item = contentItemRepository.save(item);

        // Calculate how many parts we need
        int totalParts = (int) Math.ceil((double) request.fileSizeBytes() / partSizeBytes);

        // Generate a presigned URL for EACH part
        // Each URL allows the client to PUT one chunk of the file directly to MinIO
        List<MultipartUploadInitiateResponse.PartPresignedUrl> partUrls = new ArrayList<>();
        for (int partNum = 1; partNum <= totalParts; partNum++) {
            String partUrl = storageService.generatePresignedPartUrl(
                    storageKey, uploadId, partNum, expiry);
            partUrls.add(new MultipartUploadInitiateResponse.PartPresignedUrl(partNum, partUrl));
        }

        log.info("Multipart upload initiated: contentId={}, key={}, uploadId={}, parts={}",
                item.getId(), storageKey, uploadId, totalParts);

        return new MultipartUploadInitiateResponse(
                item.getId(), uploadId, partUrls, partSizeBytes,
                totalParts, expiresAt, item.getStatus().name(), true);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // completeUpload (single-part)
    //
    // Called by the client after a single-part PUT to MinIO.
    // We verify the file exists in MinIO, update metadata, then write
    // the ContentUploadedEvent to the outbox — all in one DB transaction.
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ContentStatusResponse completeUpload(UUID contentId,
                                                 UploadCompleteRequest request,
                                                 UUID instructorId) {

        log.info("Complete upload (single-part): contentId={}, instructor={}", contentId, instructorId);

        ContentItem item = loadAndVerifyOwnership(contentId, instructorId);

        // Guard: must be in UPLOADING state
        assertStatus(item, ContentStatus.UPLOADING,
                "complete single-part upload — expected UPLOADING, got " + item.getStatus());

        // Guard: this should be a single-part upload
        if (item.isMultipart()) {
            throw new InvalidUploadException(
                    "This content was initiated as multipart. Use /complete-multipart instead.");
        }

        // Verify the file actually arrived in MinIO — throws if not found
        StorageService.StorageObjectMetadata meta;
        try {
            meta = storageService.statObject(item.getStorageKey());
        } catch (Exception e) {
            log.error("File not found in MinIO after upload: contentId={}", contentId);
            item.markFailed();
            contentItemRepository.save(item);
            throw new InvalidUploadException(
                    "File not found in storage. Please retry the upload.");
        }

        // Update with authoritative metadata from MinIO
        item.setFileSizeBytes(meta.sizeBytes());
        if (request.clientReportedDurationSeconds() != null
                && item.getContentType() == ContentType.VIDEO) {
            item.setDurationSeconds(request.clientReportedDurationSeconds());
        }
        item.markProcessing();
        item = contentItemRepository.save(item);

        // Write event to outbox — SAME transaction as above.
        // Either both commit or neither does — no dual-write split possible.
        outboxEventService.save(
                "ContentUploadedEvent",
                RabbitMQConfig.RK_CONTENT_UPLOADED,
                buildUploadedEvent(item));

        log.info("Content marked PROCESSING: contentId={}", contentId);

        // Notify any SSE subscribers on this pod (cross-pod via Redis)
        sseEmitterService.notifyStatusChange(contentId, ContentStatus.PROCESSING.name());

        return contentMapper.toStatusResponse(item);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // completeMultipartUpload
    //
    // Called by the client after ALL parts have been PUT to MinIO.
    // We tell MinIO to assemble the parts, then follow the same
    // verification + outbox + SSE notification flow as single-part.
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public ContentStatusResponse completeMultipartUpload(UUID contentId,
                                                          MultipartCompleteRequest request,
                                                          UUID instructorId) {

        log.info("Complete upload (multipart): contentId={}, parts={}, instructor={}",
                contentId, request.parts().size(), instructorId);

        ContentItem item = loadAndVerifyOwnership(contentId, instructorId);

        assertStatus(item, ContentStatus.UPLOADING,
                "complete multipart upload — expected UPLOADING, got " + item.getStatus());

        if (!item.isMultipart()) {
            throw new InvalidUploadException(
                    "This content was initiated as single-part. Use /complete instead.");
        }

        // Validate the uploadId in the request matches what we stored
        if (!request.uploadId().equals(item.getMinioUploadId())) {
            throw new InvalidUploadException(
                    "uploadId does not match the initiated upload. Expected: "
                    + item.getMinioUploadId());
        }

        // Convert request parts to StorageService domain type
        List<StorageService.CompletedPart> completedParts = request.parts().stream()
                .map(p -> new StorageService.CompletedPart(p.partNumber(), p.eTag()))
                .toList();

        // Tell MinIO to assemble all parts into the final object.
        // This is atomic on MinIO's side — either the object is created or it isn't.
        try {
            storageService.completeMultipartUpload(
                    item.getStorageKey(), request.uploadId(), completedParts);
        } catch (Exception e) {
            log.error("MinIO completeMultipart failed: contentId={}", contentId, e);
            item.markFailed();
            contentItemRepository.save(item);
            throw new InvalidUploadException(
                    "Failed to assemble upload parts. Please retry.");
        }

        // Verify the assembled object exists and get authoritative size
        StorageService.StorageObjectMetadata meta;
        try {
            meta = storageService.statObject(item.getStorageKey());
        } catch (Exception e) {
            log.error("Stat failed after multipart complete: contentId={}", contentId);
            item.markFailed();
            contentItemRepository.save(item);
            throw new InvalidUploadException(
                    "Upload assembly verification failed. Please retry.");
        }

        // Update with authoritative metadata
        item.setFileSizeBytes(meta.sizeBytes());
        if (request.clientReportedDurationSeconds() != null
                && item.getContentType() == ContentType.VIDEO) {
            item.setDurationSeconds(request.clientReportedDurationSeconds());
        }
        item.markProcessing();
        item = contentItemRepository.save(item);

        // Write event to outbox — same transaction
        outboxEventService.save(
                "ContentUploadedEvent",
                RabbitMQConfig.RK_CONTENT_UPLOADED,
                buildUploadedEvent(item));

        log.info("Multipart content marked PROCESSING: contentId={}", contentId);
        sseEmitterService.notifyStatusChange(contentId, ContentStatus.PROCESSING.name());

        return contentMapper.toStatusResponse(item);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getStatus
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public ContentStatusResponse getStatus(UUID contentId) {
        ContentItem item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found: " + contentId));
        return contentMapper.toStatusResponse(item);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // deleteContent
    //
    // Soft-delete: marks DELETED in DB, writes outbox event, triggers
    // async MinIO cleanup. Hard MinIO delete happens in the cleanup scheduler.
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional
    public void deleteContent(UUID contentId, UUID instructorId) {

        log.info("Delete content: contentId={}, instructor={}", contentId, instructorId);

        ContentItem item = loadAndVerifyOwnership(contentId, instructorId);

        if (item.getStatus() == ContentStatus.DELETED) {
            log.info("Content already deleted: contentId={}", contentId);
            return; // idempotent
        }

        // If a multipart upload was in progress, abort it to free MinIO temp storage
        if (item.isMultipart() && item.getStatus() == ContentStatus.UPLOADING) {
            storageService.abortMultipartUpload(item.getStorageKey(), item.getMinioUploadId());
        }

        item.markDeleted();
        contentItemRepository.save(item);

        // Write outbox event — downstream services (search, CDN invalidation) react
        outboxEventService.save(
                "ContentDeletedEvent",
                RabbitMQConfig.RK_CONTENT_DELETED,
                ContentDeletedEvent.builder()
                        .contentId(item.getId())
                        .lectureId(item.getLectureId())
                        .courseId(item.getCourseId())
                        .storageKey(item.getStorageKey())
                        .occurredAt(Instant.now())
                        .build());

        log.info("Content soft-deleted: contentId={}", contentId);
        sseEmitterService.notifyStatusChange(contentId, ContentStatus.DELETED.name());
        sseEmitterService.completeEmitters(contentId);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // getPresignedDownloadUrl
    // ─────────────────────────────────────────────────────────────────────────
    @Transactional(readOnly = true)
    public String getPresignedDownloadUrl(UUID contentId) {
        ContentItem item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found: " + contentId));

        if (item.getStatus() != ContentStatus.READY) {
            throw new InvalidUploadException(
                    "Content is not yet available. Current status: " + item.getStatus());
        }

        return storageService.generatePresignedDownloadUrl(
                item.getStorageKey(), Duration.ofMinutes(60));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Abandoned upload cleanup scheduler
    //
    // Finds UPLOADING records older than the threshold and marks them FAILED.
    // Also aborts any associated MinIO multipart uploads to free storage.
    // ─────────────────────────────────────────────────────────────────────────
    @Scheduled(cron = "${content.cleanup.cron:0 0 */2 * * *}")
    @Transactional
    public void cleanupAbandonedUploads() {
        Instant cutoff = Instant.now().minus(abandonedThresholdHours, ChronoUnit.HOURS);
        List<ContentItem> abandoned = contentItemRepository.findAbandonedUploads(cutoff);

        if (abandoned.isEmpty()) return;

        log.info("Cleanup: found {} abandoned uploads older than {}h", abandoned.size(), abandonedThresholdHours);

        for (ContentItem item : abandoned) {
            try {
                // Abort multipart uploads so MinIO frees the temporary part storage
                if (item.isMultipart()) {
                    storageService.abortMultipartUpload(item.getStorageKey(), item.getMinioUploadId());
                }
                item.markFailed();
                contentItemRepository.save(item);
                log.info("Abandoned upload marked FAILED: contentId={}", item.getId());
            } catch (Exception e) {
                log.error("Error cleaning up abandoned upload: contentId={}", item.getId(), e);
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────────────────

    private ContentItem loadAndVerifyOwnership(UUID contentId, UUID instructorId) {
        ContentItem item = contentItemRepository.findById(contentId)
                .orElseThrow(() -> new ResourceNotFoundException("Content not found: " + contentId));

        // Ownership check — same pattern as validateCourseOwnership in course-service
        if (!item.isOwner(instructorId)) {
            log.warn("Ownership violation: contentId={}, requestor={}, owner={}",
                    contentId, instructorId, item.getInstructorId());
            throw new ForbiddenException("You don't have permission to modify this content");
        }
        return item;
    }

    private void assertStatus(ContentItem item, ContentStatus expected, String context) {
        if (item.getStatus() != expected) {
            // If it's already past UPLOADING (PROCESSING or READY), it's likely
            // a duplicate call — return current state instead of erroring.
            if (item.getStatus() == ContentStatus.PROCESSING
                    || item.getStatus() == ContentStatus.READY) {
                // Caller should check and return current status — throw a specific
                // exception so caller can handle idempotently.
                throw new AlreadyCompletedException(item.getStatus().name());
            }
            throw new InvalidUploadException("Cannot " + context);
        }
    }

    private void validateFileSize(long fileSizeBytes, ContentType contentType) {
        long maxBytes = switch (contentType) {
            case VIDEO     -> maxVideoBytes;
            case RESOURCE  -> maxResourceBytes;
            case THUMBNAIL -> maxThumbnailBytes;
        };
        if (fileSizeBytes > maxBytes) {
            throw new InvalidUploadException(
                    String.format("File size %d bytes exceeds the %d byte limit for %s",
                            fileSizeBytes, maxBytes, contentType));
        }
    }

    private Duration expiryFor(ContentType contentType) {
        return switch (contentType) {
            case VIDEO     -> Duration.ofMinutes(videoExpiryMinutes);
            case RESOURCE  -> Duration.ofMinutes(resourceExpiryMinutes);
            case THUMBNAIL -> Duration.ofMinutes(thumbnailExpiryMinutes);
        };
    }

    private ContentItem buildContentItem(UploadInitiateRequest request,
                                          UUID instructorId,
                                          String storageKey,
                                          String uploadId) {
        return ContentItem.builder()
                .lectureId(request.lectureId())
                .courseId(request.courseId())
                .instructorId(instructorId)
                .storageKey(storageKey)
                .storageProvider(StorageProvider.MINIO)
                .contentType(request.contentType())
                .originalFilename(request.originalFilename())
                .mimeType(request.mimeType())
                .fileSizeBytes(request.fileSizeBytes())
                .minioUploadId(uploadId)
                .status(ContentStatus.UPLOADING)
                .build();
    }

    private ContentUploadedEvent buildUploadedEvent(ContentItem item) {
        return ContentUploadedEvent.builder()
                .contentId(item.getId())
                .lectureId(item.getLectureId())
                .courseId(item.getCourseId())
                .instructorId(item.getInstructorId())
                .storageKey(item.getStorageKey())
                .contentType(item.getContentType().name())
                .mimeType(item.getMimeType())
                .fileSizeBytes(item.getFileSizeBytes())
                .multipart(item.isMultipart())
                .occurredAt(Instant.now())
                .build();
    }

    /**
     * Internal signal that an operation was already performed (idempotent case).
     * Caught by the controller to return the current status instead of an error.
     */
    public static class AlreadyCompletedException extends RuntimeException {
        private final String currentStatus;
        public AlreadyCompletedException(String currentStatus) {
            super("Already completed: " + currentStatus);
            this.currentStatus = currentStatus;
        }
        public String getCurrentStatus() { return currentStatus; }
    }
}
