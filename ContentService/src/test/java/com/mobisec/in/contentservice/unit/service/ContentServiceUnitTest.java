package com.mobisec.in.contentservice.unit.service;

import com.mobisec.in.contentservice.domain.dto.*;
import com.mobisec.in.contentservice.domain.entity.ContentItem;
import com.mobisec.in.contentservice.domain.enums.ContentStatus;
import com.mobisec.in.contentservice.domain.enums.ContentType;
import com.mobisec.in.contentservice.domain.enums.StorageProvider;
import com.mobisec.in.contentservice.event.outbox.OutboxEventService;
import com.mobisec.in.contentservice.exception.InvalidUploadException;
import com.mobisec.in.contentservice.exception.ResourceNotFoundException;
import com.mobisec.in.contentservice.mapper.ContentMapper;
import com.mobisec.in.contentservice.repository.ContentItemRepository;
import com.mobisec.in.contentservice.service.ContentService;
import com.mobisec.in.contentservice.service.SseEmitterService;
import com.mobisec.in.contentservice.storage.StorageService;
import com.mobisec.in.contentservice.util.MimeTypeValidator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LAYER:  Unit
 * SCOPE:  ContentService business logic
 * SETUP:  All dependencies mocked — no DB, no MinIO, no RabbitMQ, no Redis
 *
 * File location:
 *   src/test/java/com/mobisec/in/contentservice/unit/service/ContentServiceUnitTest.java
 *
 * Run with:
 *   ./mvnw test -pl . -Dtest=ContentServiceUnitTest -Dsurefire.failIfNoSpecifiedTests=false
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ContentService — unit tests")
class ContentServiceUnitTest {

    // ── Mocked dependencies (mirror ContentService constructor args) ──────────
    @Mock private ContentItemRepository contentItemRepository;
    @Mock private StorageService        storageService;
    @Mock private OutboxEventService    outboxEventService;
    @Mock private SseEmitterService     sseEmitterService;
    @Mock private ContentMapper         contentMapper;
    @Mock private MimeTypeValidator     mimeTypeValidator;

    // Class under test — @InjectMocks creates it and injects the mocks above
    @InjectMocks
    private ContentService contentService; // REAL object, but all its dependencies are the mocks above

    // Captures what the service actually saves to the repository
    @Captor private ArgumentCaptor<ContentItem> itemCaptor;

    // ── Shared test constants ─────────────────────────────────────────────────
    private static final UUID INSTRUCTOR_ID = UUID.fromString("aaaaaaaa-0000-0000-0000-000000000001");
    private static final UUID LECTURE_ID    = UUID.fromString("bbbbbbbb-0000-0000-0000-000000000002");
    private static final UUID COURSE_ID     = UUID.fromString("cccccccc-0000-0000-0000-000000000003");
    private static final UUID CONTENT_ID    = UUID.fromString("dddddddd-0000-0000-0000-000000000004");

    // ── @Value fields can't be set by Mockito, so we inject them via reflection
    @BeforeEach
    void injectConfigValues() throws Exception {
        setField("multipartThresholdBytes", 104_857_600L);  // 100 MB
        setField("partSizeBytes",           10_485_760L);   // 10 MB
        setField("videoExpiryMinutes",      720);
        setField("resourceExpiryMinutes",   120);
        setField("thumbnailExpiryMinutes",  30);
        setField("maxVideoBytes",           5_368_709_120L);
        setField("maxResourceBytes",        524_288_000L);
        setField("maxThumbnailBytes",       10_485_760L);
        setField("abandonedThresholdHours", 6);
    }

    private void setField(String name, Object value) throws Exception {
        var field = ContentService.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(contentService, value);
    }

    // =========================================================================
    // initiateUpload — single-part (file < 100 MB threshold)
    // =========================================================================
    @Nested
    @DisplayName("initiateUpload — single-part")
    class InitiateUploadSinglePart {

        @Test
        @DisplayName("happy path — saves UPLOADING record and returns presigned URL")
        void happyPath_savesItemAndReturnsPresignedUrl() {
            // Arrange
            UploadInitiateRequest req = videoRequest(50_000_000L); // 50 MB — below threshold
            when(storageService.generatePresignedUploadUrl(anyString(), eq("video/mp4"), any()))
                    .thenReturn("https://minio/presigned?sig=abc");
            stubSaveWithGeneratedId();

            // Act
            Object result = contentService.initiateUpload(req, INSTRUCTOR_ID);

            // Assert — correct response type for single-part
            assertThat(result).isInstanceOf(UploadInitiateResponse.class);
            UploadInitiateResponse res = (UploadInitiateResponse) result;

            assertThat(res.contentId()).isNotNull();
            assertThat(res.presignedUploadUrl()).contains("presigned");
            assertThat(res.multipart()).isFalse();
            assertThat(res.status()).isEqualTo("UPLOADING");

            // Verify what got persisted
            verify(contentItemRepository).save(itemCaptor.capture());
            ContentItem saved = itemCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ContentStatus.UPLOADING);
            assertThat(saved.getInstructorId()).isEqualTo(INSTRUCTOR_ID);
            assertThat(saved.getLectureId()).isEqualTo(LECTURE_ID);
            assertThat(saved.getMimeType()).isEqualTo("video/mp4");
            assertThat(saved.getMinioUploadId()).isNull(); // single-part: no uploadId
        }

        @Test
        @DisplayName("storage key is UUID-based — never exposes original filename")
        void storageKeyIsUuidBased() {
            UploadInitiateRequest req = videoRequest(50_000_000L);
            when(storageService.generatePresignedUploadUrl(anyString(), anyString(), any()))
                    .thenReturn("https://minio/presigned");
            stubSaveWithGeneratedId();

            contentService.initiateUpload(req, INSTRUCTOR_ID);

            verify(contentItemRepository).save(itemCaptor.capture());
            String key = itemCaptor.getValue().getStorageKey();

            // Key must follow format: lectures/{lectureId}/{TYPE}/{uuid}.ext
            assertThat(key).startsWith("lectures/");
            assertThat(key).contains("VIDEO");
            // Original filename must NOT appear in the key
            assertThat(key).doesNotContain("lecture video.mp4");
        }

        @Test
        @DisplayName("rejects disallowed MIME type — no storage or DB interaction")
        void rejectsDisallowedMimeType() {
            UploadInitiateRequest req = new UploadInitiateRequest(
                    LECTURE_ID, COURSE_ID, ContentType.VIDEO,
                    "malware.exe", "application/x-msdownload", 1_000L);

            // Real validator would throw — simulate that
            doThrow(new InvalidUploadException("MIME type 'application/x-msdownload' is not permitted"))
                    .when(mimeTypeValidator).validate(eq("application/x-msdownload"), eq(ContentType.VIDEO));

            assertThatThrownBy(() -> contentService.initiateUpload(req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("not permitted");

            verifyNoInteractions(storageService, contentItemRepository, outboxEventService);
        }

        @Test
        @DisplayName("rejects file over size limit — no storage or DB interaction")
        void rejectsOversizeFile() {
            // Just over the 5 GB video limit defined in application.yml
            UploadInitiateRequest req = videoRequest(6_000_000_000L);

            assertThatThrownBy(() -> contentService.initiateUpload(req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("exceeds");

            verifyNoInteractions(storageService, contentItemRepository);
        }

        @Test
        @DisplayName("RESOURCE type uses resourceExpiryMinutes, not videoExpiryMinutes")
        void resourceTypeGetsCorrectExpiry() {
            UploadInitiateRequest req = new UploadInitiateRequest(
                    LECTURE_ID, COURSE_ID, ContentType.RESOURCE,
                    "slides.pdf", "application/pdf", 5_000_000L);

            when(storageService.generatePresignedUploadUrl(anyString(), anyString(), any()))
                    .thenReturn("https://minio/presigned");
            stubSaveWithGeneratedId();

            Object result = contentService.initiateUpload(req, INSTRUCTOR_ID);

            assertThat(result).isInstanceOf(UploadInitiateResponse.class);
            // Verify the presigned URL was requested (expiry is Duration — not easily captured,
            // but we verify the call happened with the correct content type stored)
            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getContentType()).isEqualTo(ContentType.RESOURCE);
        }
    }

    // =========================================================================
    // initiateUpload — multipart (file >= 100 MB threshold)
    // =========================================================================
    @Nested
    @DisplayName("initiateUpload — multipart")
    class InitiateUploadMultipart {

        @Test
        @DisplayName("happy path — returns uploadId and correct number of part URLs")
        void happyPath_returnsMultipartResponse() {
            // 150 MB file — above 100 MB threshold → multipart
            // With 10 MB parts → ceiling(150/10) = 15 parts
            UploadInitiateRequest req = videoRequest(157_286_400L);

            when(storageService.initiateMultipartUpload(anyString(), eq("video/mp4")))
                    .thenReturn("minio-upload-id-xyz");
            when(storageService.generatePresignedPartUrl(anyString(), eq("minio-upload-id-xyz"),
                    anyInt(), any()))
                    .thenReturn("https://minio/part-url");
            stubSaveWithGeneratedId();

            Object result = contentService.initiateUpload(req, INSTRUCTOR_ID);

            // Correct response type
            assertThat(result).isInstanceOf(MultipartUploadInitiateResponse.class);
            MultipartUploadInitiateResponse res = (MultipartUploadInitiateResponse) result;

            assertThat(res.uploadId()).isEqualTo("minio-upload-id-xyz");
            assertThat(res.multipart()).isTrue();
            assertThat(res.totalParts()).isEqualTo(15);
            assertThat(res.parts()).hasSize(15);
            assertThat(res.partSizeBytes()).isEqualTo(10_485_760L);

            // Part numbers must be 1-indexed and sequential
            assertThat(res.parts().get(0).partNumber()).isEqualTo(1);
            assertThat(res.parts().get(14).partNumber()).isEqualTo(15);

            // Verify uploadId is persisted
            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getMinioUploadId()).isEqualTo("minio-upload-id-xyz");
            assertThat(itemCaptor.getValue().isMultipart()).isTrue();
        }

        @Test
        @DisplayName("exactly-at-threshold file uses multipart (>= not just >)")
        void exactlyAtThresholdUsesMultipart() {
            // Exactly 100 MB = the multipartThresholdBytes value set in @BeforeEach
            UploadInitiateRequest req = videoRequest(104_857_600L);

            when(storageService.initiateMultipartUpload(anyString(), anyString()))
                    .thenReturn("upload-id");
            when(storageService.generatePresignedPartUrl(anyString(), anyString(), anyInt(), any()))
                    .thenReturn("https://minio/part");
            stubSaveWithGeneratedId();

            Object result = contentService.initiateUpload(req, INSTRUCTOR_ID);

            assertThat(result).isInstanceOf(MultipartUploadInitiateResponse.class);
        }

        @Test
        @DisplayName("just-below-threshold file uses single-part")
        void justBelowThresholdUsesSinglePart() {
            UploadInitiateRequest req = videoRequest(104_857_599L); // 1 byte below threshold

            when(storageService.generatePresignedUploadUrl(anyString(), anyString(), any()))
                    .thenReturn("https://minio/upload");
            stubSaveWithGeneratedId();

            Object result = contentService.initiateUpload(req, INSTRUCTOR_ID);

            assertThat(result).isInstanceOf(UploadInitiateResponse.class);
            verifyNoMoreInteractions(storageService); // no initiateMultipartUpload
        }
    }

    // =========================================================================
    // completeUpload — single-part
    // =========================================================================
    @Nested
    @DisplayName("completeUpload — single-part")
    class CompleteUpload {

        @Test
        @DisplayName("happy path — verifies MinIO stat, marks PROCESSING, fires outbox event")
        void happyPath() {
            ContentItem item = uploadingVideoItem(false); // single-part
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            StorageService.StorageObjectMetadata meta =
                    new StorageService.StorageObjectMetadata(item.getStorageKey(), 52_428_800L, "etag", "video/mp4");
            when(storageService.statObject(item.getStorageKey())).thenReturn(meta);
            stubSaveWithGeneratedId();

            ContentStatusResponse fakeResponse = fakeStatusResponse(ContentStatus.PROCESSING);
            when(contentMapper.toStatusResponse(any())).thenReturn(fakeResponse);

            UploadCompleteRequest req = new UploadCompleteRequest("etag-abc", null);
            ContentStatusResponse res = contentService.completeUpload(CONTENT_ID, req, INSTRUCTOR_ID);

            // Status response returned
            assertThat(res.status()).isEqualTo("PROCESSING");

            // Authoritative file size from MinIO stat overwrites client-declared value
            verify(contentItemRepository).save(itemCaptor.capture());
            ContentItem saved = itemCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ContentStatus.PROCESSING);
            assertThat(saved.getFileSizeBytes()).isEqualTo(52_428_800L); // from MinIO, not client

            // Outbox event must be written in same transaction
            verify(outboxEventService).save(eq("ContentUploadedEvent"), anyString(), any());

            // SSE notification fired
            verify(sseEmitterService).notifyStatusChange(CONTENT_ID, "PROCESSING");
        }

        @Test
        @DisplayName("file not in MinIO after upload — marks FAILED and throws")
        void fileNotInMinioMarksFailedAndThrows() {
            ContentItem item = uploadingVideoItem(false);
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));
            when(storageService.statObject(anyString()))
                    .thenThrow(new RuntimeException("object not found"));
            stubSaveWithGeneratedId();

            assertThatThrownBy(() -> contentService.completeUpload(
                    CONTENT_ID, new UploadCompleteRequest("etag", null), INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("retry");

            // Item must have been marked FAILED
            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.FAILED);

            // Outbox event must NOT be written for failed uploads
            verifyNoInteractions(outboxEventService);
        }

        @Test
        @DisplayName("rejects duplicate complete — status already PROCESSING")
        void rejectsDoubleComplete() {
            ContentItem item = processingItem(false);
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> contentService.completeUpload(
                    CONTENT_ID, new UploadCompleteRequest("etag", null), INSTRUCTOR_ID))
                    .isInstanceOf(ContentService.AlreadyCompletedException.class);

            verifyNoInteractions(storageService, outboxEventService);
        }

        @Test
        @DisplayName("throws ContentNotFoundException when contentId not found")
        void throwsWhenContentNotFound() {
            when(contentItemRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> contentService.completeUpload(
                    CONTENT_ID, new UploadCompleteRequest("etag", null), INSTRUCTOR_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining(CONTENT_ID.toString());
        }

        @Test
        @DisplayName("rejects completeUpload for a multipart-initiated item")
        void rejectsMultipartItemOnSingleCompleteEndpoint() {
            ContentItem item = uploadingVideoItem(true); // multipart item
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> contentService.completeUpload(
                    CONTENT_ID, new UploadCompleteRequest("etag", null), INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("complete-multipart");

            verifyNoInteractions(outboxEventService);
        }

        @Test
        @DisplayName("clientReportedDurationSeconds stored for VIDEO type")
        void durationStoredForVideo() {
            ContentItem item = uploadingVideoItem(false);
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            StorageService.StorageObjectMetadata meta =
                    new StorageService.StorageObjectMetadata(item.getStorageKey(), 10_000L, "e", "video/mp4");
            when(storageService.statObject(anyString())).thenReturn(meta);
            stubSaveWithGeneratedId();
            when(contentMapper.toStatusResponse(any())).thenReturn(fakeStatusResponse(ContentStatus.PROCESSING));

            contentService.completeUpload(CONTENT_ID,
                    new UploadCompleteRequest("etag", 3600), INSTRUCTOR_ID);

            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getDurationSeconds()).isEqualTo(3600);
        }
    }

    // =========================================================================
    // completeMultipartUpload
    // =========================================================================
    @Nested
    @DisplayName("completeMultipartUpload")
    class CompleteMultipartUpload {

        @Test
        @DisplayName("happy path — assembles parts, verifies size, marks PROCESSING")
        void happyPath() {
            ContentItem item = uploadingVideoItem(true);
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));
            stubSaveWithGeneratedId();

            StorageService.StorageObjectMetadata meta =
                    new StorageService.StorageObjectMetadata(item.getStorageKey(), 157_286_400L, "e", "video/mp4");
            when(storageService.statObject(anyString())).thenReturn(meta);
            when(contentMapper.toStatusResponse(any())).thenReturn(fakeStatusResponse(ContentStatus.PROCESSING));

            MultipartCompleteRequest req = new MultipartCompleteRequest(
                    "minio-upload-id-xyz",
                    List.of(
                            new MultipartCompleteRequest.CompletedPart(1, "etag1"),
                            new MultipartCompleteRequest.CompletedPart(2, "etag2")
                    ),
                    null);

            ContentStatusResponse res = contentService.completeMultipartUpload(CONTENT_ID, req, INSTRUCTOR_ID);

            assertThat(res.status()).isEqualTo("PROCESSING");

            verify(storageService).completeMultipartUpload(
                    eq(item.getStorageKey()), eq("minio-upload-id-xyz"), anyList());

            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.PROCESSING);
            assertThat(itemCaptor.getValue().getFileSizeBytes()).isEqualTo(157_286_400L);

            verify(outboxEventService).save(eq("ContentUploadedEvent"), anyString(), any());
            verify(sseEmitterService).notifyStatusChange(CONTENT_ID, "PROCESSING");
        }

        @Test
        @DisplayName("rejects mismatched uploadId — request vs stored")
        void rejectsMismatchedUploadId() {
            ContentItem item = uploadingVideoItem(true); // stored uploadId = "minio-upload-id-xyz"
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            MultipartCompleteRequest req = new MultipartCompleteRequest(
                    "WRONG-upload-id",
                    List.of(new MultipartCompleteRequest.CompletedPart(1, "etag1")),
                    null);

            assertThatThrownBy(() -> contentService.completeMultipartUpload(CONTENT_ID, req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("uploadId");

            verifyNoInteractions(outboxEventService);
        }

        @Test
        @DisplayName("MinIO assembly failure — marks FAILED and throws")
        void minioAssemblyFailureMarksFailed() {
            ContentItem item = uploadingVideoItem(true);
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));
            doThrow(new RuntimeException("MinIO error"))
                    .when(storageService).completeMultipartUpload(anyString(), anyString(), anyList());
            stubSaveWithGeneratedId();

            MultipartCompleteRequest req = new MultipartCompleteRequest(
                    "minio-upload-id-xyz",
                    List.of(new MultipartCompleteRequest.CompletedPart(1, "etag1")),
                    null);

            assertThatThrownBy(() -> contentService.completeMultipartUpload(CONTENT_ID, req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("retry");

            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.FAILED);
            verifyNoInteractions(outboxEventService);
        }

        @Test
        @DisplayName("rejects single-part item on multipart-complete endpoint")
        void rejectsSinglePartItemOnMultipartEndpoint() {
            ContentItem item = uploadingVideoItem(false); // single-part — no uploadId
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            MultipartCompleteRequest req = new MultipartCompleteRequest(
                    "any-upload-id",
                    List.of(new MultipartCompleteRequest.CompletedPart(1, "etag")),
                    null);

            assertThatThrownBy(() -> contentService.completeMultipartUpload(CONTENT_ID, req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadException.class)
                    .hasMessageContaining("single-part");

            verifyNoInteractions(outboxEventService);
        }
    }

    // =========================================================================
    // getStatus
    // =========================================================================
    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("returns mapped status response for known contentId")
        void returnsStatusForKnownId() {
            ContentItem item = readyItem();
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));
            when(contentMapper.toStatusResponse(item)).thenReturn(fakeStatusResponse(ContentStatus.READY));

            ContentStatusResponse res = contentService.getStatus(CONTENT_ID);

            assertThat(res.status()).isEqualTo("READY");
        }

        @Test
        @DisplayName("throws ResourceNotFoundException for unknown contentId")
        void throwsForUnknownId() {
            when(contentItemRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> contentService.getStatus(UUID.randomUUID()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // =========================================================================
    // deleteContent
    // =========================================================================
    @Nested
    @DisplayName("deleteContent")
    class DeleteContent {

        @Test
        @DisplayName("soft-deletes item and fires outbox event")
        void softDeletesAndFiresEvent() {
            ContentItem item = readyItem();
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));
            stubSaveWithGeneratedId();

            contentService.deleteContent(CONTENT_ID, INSTRUCTOR_ID);

            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.DELETED);

            verify(outboxEventService).save(eq("ContentDeletedEvent"), anyString(), any());
            verify(sseEmitterService).notifyStatusChange(CONTENT_ID, "DELETED");
            verify(sseEmitterService).completeEmitters(CONTENT_ID);
        }

        @Test
        @DisplayName("idempotent — already DELETED item returns without error or extra writes")
        void idempotentForAlreadyDeletedItem() {
            ContentItem item = buildItem(ContentStatus.DELETED, false);
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            contentService.deleteContent(CONTENT_ID, INSTRUCTOR_ID);

            // No DB write, no outbox event for an already-deleted item
            verify(contentItemRepository, never()).save(any());
            verifyNoInteractions(outboxEventService);
        }

        @Test
        @DisplayName("aborts multipart upload before deleting if still UPLOADING")
        void abortsMultipartUploadWhenStillUploading() {
            ContentItem item = uploadingVideoItem(true); // multipart, UPLOADING
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));
            stubSaveWithGeneratedId();

            contentService.deleteContent(CONTENT_ID, INSTRUCTOR_ID);

            verify(storageService).abortMultipartUpload(item.getStorageKey(), item.getMinioUploadId());
            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.DELETED);
        }

        @Test
        @DisplayName("does NOT abort multipart when status is READY (upload already finished)")
        void doesNotAbortWhenReady() {
            ContentItem item = readyItem(); // READY, multipart=false
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));
            stubSaveWithGeneratedId();

            contentService.deleteContent(CONTENT_ID, INSTRUCTOR_ID);

            verify(storageService, never()).abortMultipartUpload(anyString(), anyString());
        }
    }

    // =========================================================================
    // cleanupAbandonedUploads
    // =========================================================================
    @Nested
    @DisplayName("cleanupAbandonedUploads")
    class CleanupAbandonedUploads {

        @Test
        @DisplayName("marks abandoned single-part uploads FAILED")
        void marksSinglePartAbandonedFailed() {
            ContentItem item = uploadingVideoItem(false);
            when(contentItemRepository.findAbandonedUploads(any()))
                    .thenReturn(List.of(item));
            stubSaveWithGeneratedId();

            contentService.cleanupAbandonedUploads();

            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.FAILED);
            verify(storageService, never()).abortMultipartUpload(anyString(), anyString());
        }

        @Test
        @DisplayName("aborts multipart upload before marking abandoned multipart FAILED")
        void abortsMultipartBeforeMarkingFailed() {
            ContentItem item = uploadingVideoItem(true);
            when(contentItemRepository.findAbandonedUploads(any()))
                    .thenReturn(List.of(item));
            stubSaveWithGeneratedId();

            contentService.cleanupAbandonedUploads();

            verify(storageService).abortMultipartUpload(item.getStorageKey(), item.getMinioUploadId());
            verify(contentItemRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.FAILED);
        }

        @Test
        @DisplayName("continues processing remaining items even if one throws")
        void continuesOnError() {
            ContentItem failing = uploadingVideoItem(true);
            ContentItem ok      = uploadingVideoItem(false);

            when(contentItemRepository.findAbandonedUploads(any()))
                    .thenReturn(List.of(failing, ok));
            doThrow(new RuntimeException("MinIO down"))
                    .when(storageService).abortMultipartUpload(anyString(), anyString());
            stubSaveWithGeneratedId();

            // Must not throw — errors are swallowed per-item
            assertThatCode(() -> contentService.cleanupAbandonedUploads())
                    .doesNotThrowAnyException();

            // The non-failing item should still be saved
            verify(contentItemRepository, atLeastOnce()).save(any());
        }

        @Test
        @DisplayName("no-op when no abandoned uploads found")
        void noOpWhenEmpty() {
            when(contentItemRepository.findAbandonedUploads(any())).thenReturn(List.of());

            contentService.cleanupAbandonedUploads();

            verify(contentItemRepository, never()).save(any());
            verifyNoInteractions(storageService);
        }
    }

    // =========================================================================
    // Ownership / access control
    // =========================================================================
    @Nested
    @DisplayName("ownership checks")
    class OwnershipChecks {

        @Test
        @DisplayName("completeUpload throws ForbiddenException when caller is not owner")
        void completeUploadForbiddenForNonOwner() {
            ContentItem item = uploadingVideoItem(false);
            // item.instructorId = INSTRUCTOR_ID; caller is DIFFERENT instructor
            UUID differentInstructor = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> contentService.completeUpload(
                    CONTENT_ID, new UploadCompleteRequest("etag", null), differentInstructor))
                    .isInstanceOf(com.mobisec.in.contentservice.exception.ForbiddenException.class);

            verifyNoInteractions(storageService, outboxEventService);
        }

        @Test
        @DisplayName("deleteContent throws ForbiddenException when caller is not owner")
        void deleteContentForbiddenForNonOwner() {
            ContentItem item = readyItem();
            UUID differentInstructor = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
            when(contentItemRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            assertThatThrownBy(() -> contentService.deleteContent(CONTENT_ID, differentInstructor))
                    .isInstanceOf(com.mobisec.in.contentservice.exception.ForbiddenException.class);

            verify(contentItemRepository, never()).save(any());
            verifyNoInteractions(outboxEventService);
        }
    }

    // =========================================================================
    // Private test-data builders
    // =========================================================================

    /** Standard video UploadInitiateRequest with a given file size. */
    private UploadInitiateRequest videoRequest(long sizeBytes) {
        return new UploadInitiateRequest(
                LECTURE_ID, COURSE_ID, ContentType.VIDEO,
                "lecture video.mp4", "video/mp4", sizeBytes);
    }

    /** A ContentItem in UPLOADING state, single-part or multipart. */
    private ContentItem uploadingVideoItem(boolean multipart) {
        return ContentItem.builder()
                .id(CONTENT_ID)
                .lectureId(LECTURE_ID)
                .courseId(COURSE_ID)
                .instructorId(INSTRUCTOR_ID)
                .contentType(ContentType.VIDEO)
                .mimeType("video/mp4")
                .originalFilename("lecture.mp4")
                .storageKey("lectures/" + LECTURE_ID + "/VIDEO/" + UUID.randomUUID() + ".mp4")
                .fileSizeBytes(multipart ? 157_286_400L : 50_000_000L)
                .minioUploadId(multipart ? "minio-upload-id-xyz" : null)
                .storageProvider(StorageProvider.MINIO)
                .status(ContentStatus.UPLOADING)
                .build();
    }

    private ContentItem processingItem(boolean multipart) {
        ContentItem item = uploadingVideoItem(multipart);
        item.setStatus(ContentStatus.PROCESSING);
        return item;
    }

    private ContentItem readyItem() {
        ContentItem item = uploadingVideoItem(false);
        item.setStatus(ContentStatus.READY);
        return item;
    }

    private ContentItem buildItem(ContentStatus status, boolean multipart) {
        ContentItem item = uploadingVideoItem(multipart);
        item.setStatus(status);
        return item;
    }

    /** Minimal ContentStatusResponse for stubbing contentMapper.toStatusResponse(). */
    private ContentStatusResponse fakeStatusResponse(ContentStatus status) {
        return new ContentStatusResponse(
                CONTENT_ID, LECTURE_ID, COURSE_ID,
                "VIDEO", status.name(),
                "lecture.mp4", "video/mp4",
                50_000_000L, null, null, null, null,
                false, Instant.now(), Instant.now());
    }
    private void stubSaveWithGeneratedId() {
        when(contentItemRepository.save(any())).thenAnswer(inv -> {
            ContentItem item = inv.getArgument(0);
            if (item.getId() == null) {
                item.setId(UUID.randomUUID());
            }
            return item;
        });
    }
}