package com.lms.content.service.impl;

import com.lms.content.domain.entity.ContentItem;
import com.lms.content.domain.enums.ContentStatus;
import com.lms.content.domain.enums.ContentType;
import com.lms.content.domain.enums.StorageProvider;
import com.lms.content.dto.request.ContentUploadRequest;
import com.lms.content.dto.response.ContentItemResponse;
import com.lms.content.dto.response.SignedUrlResponse;
import com.lms.content.exception.ContentNotFoundException;
import com.lms.content.exception.OwnershipException;
import com.lms.content.mapper.ContentItemMapper;
import com.lms.content.messaging.ContentEventPublisher;
import com.lms.content.repository.ContentItemRepository;
import com.lms.content.service.ContentService;
import com.lms.content.service.storage.StorageService;
import com.lms.content.util.FileValidationUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Core content service implementation.
 *
 * <p>Key design decisions:
 * <ul>
 *   <li><b>Upload flow</b>: validate → save record as UPLOADING → async storage upload
 *       → update to READY. If async fails, record stays FAILED and can be retried.</li>
 *   <li><b>Signed URLs</b>: generated on-demand, not stored in DB, to avoid stale URLs
 *       and to allow TTL changes without migration.</li>
 *   <li><b>Caching</b>: lecture content lists are cached in Redis; invalidated on upload/delete.
 *       Signed URLs are NOT cached (they are already time-limited and cheap to generate).</li>
 *   <li><b>Ownership checks</b>: always validated before mutation; admins can bypass.</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)   // default read-only; mutating methods override
public class ContentServiceImpl implements ContentService {

    private final ContentItemRepository contentRepository;
    private final StorageService storageService;
    private final ContentItemMapper contentMapper;
    private final ContentEventPublisher eventPublisher;

    @Value("${cloudinary.signed-url-expiry-seconds:3600}")
    private long signedUrlExpiry;

    // ─── Upload ───────────────────────────────────────────────────────────

    /**
     * Orchestrates the upload workflow.
     *
     * <p>We persist the DB record BEFORE uploading to storage so that:
     * <ul>
     *   <li>If storage upload fails, we have a FAILED record we can retry.</li>
     *   <li>The client gets an immediate response with the content ID.</li>
     *   <li>The async upload doesn't block the HTTP thread.</li>
     * </ul>
     */
    @Override
    @Transactional
    @CacheEvict(value = "lectureContent", key = "#request.lectureId")
    public ContentItemResponse upload(ContentUploadRequest request, UUID uploaderId) {
        log.info("Starting upload: lectureId=[{}], type=[{}], uploader=[{}]",
                 request.getLectureId(), request.getContentType(), uploaderId);

        // Step 1: Validate file (MIME + size) — throws InvalidFileException on failure
        String detectedMime = FileValidationUtil.validateAndDetectMime(
            request.getFile(), request.getContentType()
        );

        // Step 2: Persist metadata record as UPLOADING
        ContentItem item = ContentItem.builder()
            .lectureId(request.getLectureId())
            .courseId(request.getCourseId())
            .instructorId(uploaderId)
            .contentType(request.getContentType())
            .originalFilename(request.getFile().getOriginalFilename())
            .mimeType(detectedMime)
            .fileSizeBytes(request.getFile().getSize())
            .status(ContentStatus.UPLOADING)
            .storageProvider(StorageProvider.CLOUDINARY)  // resolved from config
            .storageKey("pending")                         // placeholder until async upload
            .build();

        ContentItem saved = contentRepository.save(item);
        log.debug("Persisted ContentItem id=[{}] as UPLOADING", saved.getId());

        // Step 3: Async upload to storage (does not block HTTP response)
        performAsyncUpload(saved.getId(), request);

        log.info("Upload initiated for contentId=[{}]", saved.getId());
        return contentMapper.toResponse(saved);
    }

    /**
     * Performs the actual storage upload asynchronously.
     *
     * <p>Runs in a dedicated thread pool (configured in {@link com.lms.content.config.AsyncConfig}).
     * On success, updates the DB record to READY and publishes an event.
     * On failure, updates to FAILED — allowing retry logic or manual intervention.
     *
     * <p>We load the entity by ID here (within the async thread's transaction)
     * to avoid detached entity issues from passing the entity across thread boundaries.
     */
    @Async("uploadExecutor")
    @Transactional
    public void performAsyncUpload(UUID contentId, ContentUploadRequest request) {
        log.info("[Async] Starting storage upload for contentId=[{}]", contentId);
        ContentItem item = contentRepository.findById(contentId)
            .orElseThrow(() -> new ContentNotFoundException("Content not found: " + contentId));
        try {
            String resourceType = FileValidationUtil.resourceTypeFor(request.getContentType());
            String folder       = FileValidationUtil.folderFor(request.getContentType());

            StorageService.UploadResult result =
                storageService.upload(request.getFile(), folder, resourceType);

            item.setStorageKey(result.storageKey());
            item.setFileSizeBytes(result.fileSizeBytes());
            item.setDurationSeconds(result.durationSeconds());
            item.setWidthPixels(result.widthPixels());
            item.setHeightPixels(result.heightPixels());
            item.markReady();

            contentRepository.save(item);
            log.info("[Async] Upload complete for contentId=[{}], storageKey=[{}]",
                     contentId, result.storageKey());

            // Publish event for downstream services (notifications, stats, future transcoding)
            eventPublisher.publishContentUploaded(item);

        } catch (Exception e) {
            log.error("[Async] Upload FAILED for contentId=[{}]: {}", contentId, e.getMessage(), e);
            item.markFailed();
            contentRepository.save(item);
        }
    }

    // ─── Signed URL ───────────────────────────────────────────────────────

    /**
     * Generates a signed URL for content access.
     *
     * <p>We do NOT cache signed URLs in Redis because:
     * <ul>
     *   <li>Each URL has a built-in TTL — caching it reduces effective security.</li>
     *   <li>URL generation is a cheap HMAC/signature operation (microseconds).</li>
     *   <li>Caching signed URLs with user-scoped keys would create large cache entries.</li>
     * </ul>
     */
    @Override
    public SignedUrlResponse generateSignedUrl(UUID contentId, UUID requesterId, boolean isAdmin) {
        log.info("Generating signed URL: contentId=[{}], requester=[{}]", contentId, requesterId);

        ContentItem item = contentRepository.findByIdAndStatus(contentId, ContentStatus.READY)
            .orElseThrow(() -> new ContentNotFoundException(
                "Content not found or not ready: " + contentId));

        // Authorization: owner or admin
        if (!isAdmin && !item.isOwner(requesterId)) {
            // Note: enrolled students would also be allowed here.
            // In production, check enrollment via Redis cache or a shared enrollment check.
            // For now, only owner + admin are allowed.
            log.warn("Access denied: requester=[{}] is not owner of contentId=[{}]",
                     requesterId, contentId);
            throw new OwnershipException("Access denied to content: " + contentId);
        }

        String resourceType = FileValidationUtil.resourceTypeFor(item.getContentType());
        String signedUrl    = storageService.generateSignedUrl(
            item.getStorageKey(), resourceType, signedUrlExpiry);

        long expiresAt = System.currentTimeMillis() / 1000L + signedUrlExpiry;

        log.info("Signed URL generated for contentId=[{}], expiresAt=[{}]", contentId, expiresAt);
        return SignedUrlResponse.builder()
            .contentId(contentId)
            .signedUrl(signedUrl)
            .expiresAt(expiresAt)
            .ttlSeconds(signedUrlExpiry)
            .build();
    }

    // ─── Queries ──────────────────────────────────────────────────────────

    /**
     * Cached lecture content list.
     *
     * <p>Cache key is the lectureId. Evicted on upload/delete.
     * TTL configured in {@link com.lms.content.config.CacheConfig}.
     */
    @Override
    @Cacheable(value = "lectureContent", key = "#lectureId")
    public List<ContentItemResponse> getLectureContent(UUID lectureId, UUID requesterId, boolean isAdmin) {
        log.debug("Fetching content for lectureId=[{}]", lectureId);
        List<ContentItem> items = contentRepository.findByLectureId(lectureId);
        return items.stream().map(contentMapper::toResponse).toList();
    }

    @Override
    public Page<ContentItemResponse> getInstructorContent(
        UUID instructorId, ContentType contentType, Pageable pageable) {

        return contentRepository
            .findByInstructorIdAndContentTypeAndStatusNot(
                instructorId, contentType, ContentStatus.DELETED, pageable)
            .map(contentMapper::toResponse);
    }

    // ─── Delete ───────────────────────────────────────────────────────────

    /**
     * Soft-deletes the record and asynchronously removes from storage.
     *
     * <p>Soft-delete first (instant) prevents further access.
     * Storage cleanup is async — storage failures don't fail the client request.
     * A periodic cleanup job can handle orphaned storage objects if needed.
     */
    @Override
    @Transactional
    @CacheEvict(value = "lectureContent", key = "#result")
    public void delete(UUID contentId, UUID requesterId, boolean isAdmin) {
        log.info("Deleting contentId=[{}] by requester=[{}]", contentId, requesterId);

        ContentItem item = contentRepository.findById(contentId)
            .orElseThrow(() -> new ContentNotFoundException("Content not found: " + contentId));

        if (!isAdmin && !item.isOwner(requesterId)) {
            throw new OwnershipException("You do not own this content: " + contentId);
        }

        String storageKey   = item.getStorageKey();
        String resourceType = FileValidationUtil.resourceTypeFor(item.getContentType());

        // Soft delete in DB (immediate)
        item.markDeleted();
        contentRepository.save(item);
        log.info("ContentItem=[{}] marked as DELETED", contentId);

        // Async storage deletion (non-blocking, failure-tolerant)
        asyncDeleteFromStorage(storageKey, resourceType, contentId);

        // Notify downstream (e.g., Course Service can update lecture metadata)
        eventPublisher.publishContentDeleted(item);
    }

    @Async("uploadExecutor")
    protected void asyncDeleteFromStorage(String storageKey, String resourceType, UUID contentId) {
        log.debug("[Async] Deleting from storage: storageKey=[{}]", storageKey);
        try {
            storageService.delete(storageKey, resourceType);
            log.info("[Async] Storage delete complete for contentId=[{}]", contentId);
        } catch (Exception e) {
            // Log but do not re-throw — the DB record is already soft-deleted.
            // A cleanup job should handle orphaned storage objects.
            log.error("[Async] Storage delete failed for contentId=[{}]: {}", contentId, e.getMessage(), e);
        }
    }

    // ─── Extension: Transcoding callback ──────────────────────────────────

    /**
     * Called by the future video transcoding service via RabbitMQ when HLS encoding completes.
     *
     * <p>This is an extension point — wiring it up requires:
     * <ol>
     *   <li>A transcoding service (FFmpeg-based) consuming transcode.request events.</li>
     *   <li>That service publishing transcode.complete events with contentId + hlsManifestKey.</li>
     *   <li>{@link com.lms.content.messaging.ContentEventListener} routing the event here.</li>
     * </ol>
     */
    @Override
    @Transactional
    public void onTranscodeComplete(UUID contentId, String hlsManifestKey) {
        log.info("Transcode complete for contentId=[{}], hlsManifestKey=[{}]", contentId, hlsManifestKey);
        ContentItem item = contentRepository.findById(contentId)
            .orElseThrow(() -> new ContentNotFoundException("Content not found: " + contentId));
        item.setHlsManifestKey(hlsManifestKey);
        item.markReady();
        contentRepository.save(item);
    }
}
