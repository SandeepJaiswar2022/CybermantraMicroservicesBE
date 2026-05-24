package com.example.content.unit;

import com.example.content.config.TestDataFactory;
import com.example.content.config.TestJwtFactory;
import com.example.content.dto.request.*;
import com.example.content.dto.response.*;
import com.example.content.entity.*;
import com.example.content.event.ContentEventPublisher;
import com.example.content.exception.*;
import com.example.content.repository.ContentItemRepository;
import com.example.content.service.impl.UploadServiceImpl;
import com.example.content.storage.StorageService;
import com.example.content.config.MinioProperties;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * LAYER: Unit
 * SCOPE: UploadServiceImpl business logic
 * DEPENDENCIES: All mocked — no DB, no MinIO, no RabbitMQ
 *
 * What we verify here:
 *  - Correct state transitions
 *  - Validation rules (MIME, size, ownership, status guards)
 *  - Correct calls to storage + event publisher
 *  - Error paths throw the right exceptions
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UploadService — unit tests")
class UploadServiceUnitTest {

    @Mock ContentItemRepository   contentRepository;
    @Mock StorageService          storageService;
    @Mock ContentEventPublisher   eventPublisher;
    @Mock MinioProperties         minioProps;
    @Mock MinioProperties.Buckets buckets;

    @InjectMocks UploadServiceImpl uploadService;

    @Captor ArgumentCaptor<ContentItem> itemCaptor;

    private static final UUID INSTRUCTOR_ID = TestJwtFactory.INSTRUCTOR_ID;
    private static final UUID CONTENT_ID    = UUID.randomUUID();

    @BeforeEach
    void setup() {
        when(minioProps.getPresignedUrlExpiryMinutes()).thenReturn(15);
        when(minioProps.getBuckets()).thenReturn(buckets);
        when(buckets.getRawVideos()).thenReturn("raw-videos");
        when(buckets.getRawImages()).thenReturn("raw-images");
        when(buckets.getRawDocs()).thenReturn("raw-docs");
        when(storageService.bucketFor(any())).thenReturn("raw-videos");
    }

    // ── initiateUpload ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("initiateUpload")
    class InitiateUpload {

        @Test
        @DisplayName("happy path — saves UPLOADING record and returns presigned URL")
        void happyPath() {
            when(storageService.generatePresignedPutUrl(anyString(), anyInt()))
                    .thenReturn("https://minio/presigned?sig=abc");
            when(contentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InitiateUploadRequest req = TestDataFactory.videoInitiateRequest();
            InitiateUploadResponse res = uploadService.initiateUpload(req, INSTRUCTOR_ID);

            assertThat(res.getContentId()).isNotNull();
            assertThat(res.getPresignedUrl()).contains("presigned");
            assertThat(res.getStorageKey()).startsWith("video/");
            assertThat(res.getExpiresInMinutes()).isEqualTo(15);

            verify(contentRepository).save(itemCaptor.capture());
            ContentItem saved = itemCaptor.getValue();
            assertThat(saved.getStatus()).isEqualTo(ContentStatus.UPLOADING);
            assertThat(saved.getInstructorId()).isEqualTo(INSTRUCTOR_ID);
            assertThat(saved.getStorageKey()).doesNotContain("pending");
        }

        @Test
        @DisplayName("rejects disallowed MIME type with InvalidUploadStateException")
        void rejectsBadMime() {
            InitiateUploadRequest req = TestDataFactory.invalidMimeRequest();

            assertThatThrownBy(() -> uploadService.initiateUpload(req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadStateException.class)
                    .hasMessageContaining("not allowed");

            verifyNoInteractions(storageService, contentRepository, eventPublisher);
        }

        @Test
        @DisplayName("rejects file over size limit")
        void rejectsOversize() {
            InitiateUploadRequest req = TestDataFactory.oversizeRequest();

            assertThatThrownBy(() -> uploadService.initiateUpload(req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadStateException.class)
                    .hasMessageContaining("exceeds");

            verifyNoInteractions(storageService, contentRepository);
        }

        @Test
        @DisplayName("object key is sanitised — no special characters")
        void objectKeyIsSanitised() {
            InitiateUploadRequest req = TestDataFactory.videoInitiateRequest();
            req.setFilename("my lecture (final) v2!.mp4");

            when(storageService.generatePresignedPutUrl(anyString(), anyInt()))
                    .thenReturn("https://minio/presigned");
            when(contentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InitiateUploadResponse res = uploadService.initiateUpload(req, INSTRUCTOR_ID);

            assertThat(res.getStorageKey()).doesNotContain(" ", "(", ")", "!");
        }

        @Test
        @DisplayName("PDF upload uses doc bucket prefix")
        void pdfUseDocPrefix() {
            when(storageService.bucketFor(ContentType.PDF)).thenReturn("raw-docs");
            when(storageService.generatePresignedPutUrl(anyString(), anyInt()))
                    .thenReturn("https://minio/presigned");
            when(contentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InitiateUploadRequest req = TestDataFactory.pdfInitiateRequest();
            InitiateUploadResponse res = uploadService.initiateUpload(req, INSTRUCTOR_ID);

            assertThat(res.getStorageKey()).startsWith("doc/");
        }
    }

    // ── completeUpload ────────────────────────────────────────────────────────

    @Nested
    @DisplayName("completeUpload")
    class CompleteUpload {

        @Test
        @DisplayName("happy path — verifies ETag, marks PROCESSING, fires event")
        void happyPath() {
            ContentItem item = TestDataFactory.uploadingVideoItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));
            when(storageService.verifyObject(anyString(), anyString(), anyString()))
                    .thenReturn(52_428_800L);
            when(contentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            CompleteUploadRequest req = TestDataFactory.completeRequest(
                    "\"abc123\"", item.getStorageKey());
            uploadService.completeUpload(CONTENT_ID, req, INSTRUCTOR_ID);

            verify(storageService).verifyObject(anyString(), eq(item.getStorageKey()), eq("\"abc123\""));
            verify(contentRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getStatus()).isEqualTo(ContentStatus.PROCESSING);
            assertThat(itemCaptor.getValue().getETag()).isEqualTo("\"abc123\"");
            verify(eventPublisher).publishContentUploaded(any());
        }

        @Test
        @DisplayName("rejects tampered storageKey")
        void rejectsTamperedStorageKey() {
            ContentItem item = TestDataFactory.uploadingVideoItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));

            CompleteUploadRequest req = TestDataFactory.tamperedStorageKey();

            assertThatThrownBy(() -> uploadService.completeUpload(CONTENT_ID, req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadStateException.class)
                    .hasMessageContaining("mismatch");

            verifyNoInteractions(storageService, eventPublisher);
        }

        @Test
        @DisplayName("rejects double-complete — status already PROCESSING")
        void rejectsDoubleComplete() {
            ContentItem item = TestDataFactory.processingItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));

            CompleteUploadRequest req = TestDataFactory.completeRequest(
                    "\"etag\"", item.getStorageKey());

            assertThatThrownBy(() -> uploadService.completeUpload(CONTENT_ID, req, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadStateException.class)
                    .hasMessageContaining("PROCESSING");
        }

        @Test
        @DisplayName("throws ContentNotFoundException when contentId not found")
        void throwsWhenNotFound() {
            when(contentRepository.findByIdAndInstructorId(any(), any()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> uploadService.completeUpload(
                    CONTENT_ID, TestDataFactory.completeRequest("etag", "key"), INSTRUCTOR_ID))
                    .isInstanceOf(ContentNotFoundException.class);
        }

        @Test
        @DisplayName("updates fileSizeBytes from MinIO stat — not from client declaration")
        void updatesFileSizeFromMinIO() {
            ContentItem item = TestDataFactory.uploadingVideoItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);
            item.setFileSizeBytes(100L);   // client declared wrong size

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));
            when(storageService.verifyObject(anyString(), anyString(), anyString()))
                    .thenReturn(52_428_800L);   // real size from MinIO
            when(contentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            uploadService.completeUpload(CONTENT_ID,
                    TestDataFactory.completeRequest("etag", item.getStorageKey()), INSTRUCTOR_ID);

            verify(contentRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getFileSizeBytes()).isEqualTo(52_428_800L);
        }
    }

    // ── initiateMultipartUpload ───────────────────────────────────────────────

    @Nested
    @DisplayName("initiateMultipartUpload")
    class InitiateMultipart {

        @Test
        @DisplayName("happy path — creates uploadId and returns correct number of part URLs")
        void happyPath() {
            ContentItem item = TestDataFactory.uploadingVideoItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));
            when(storageService.createMultipartUpload(anyString(), anyString(), anyString()))
                    .thenReturn("upload-id-xyz");
            when(storageService.generatePresignedPartUrls(anyString(), anyString(),
                    anyString(), eq(3), anyInt()))
                    .thenReturn(List.of("url1", "url2", "url3"));
            when(contentRepository.save(any())).thenAnswer(i -> i.getArgument(0));

            InitiateMultipartResponse res = uploadService.initiateMultipartUpload(
                    CONTENT_ID, TestDataFactory.multipartRequest(3), INSTRUCTOR_ID);

            assertThat(res.getUploadId()).isEqualTo("upload-id-xyz");
            assertThat(res.getPresignedUrls()).hasSize(3);

            verify(contentRepository).save(itemCaptor.capture());
            assertThat(itemCaptor.getValue().getUploadId()).isEqualTo("upload-id-xyz");
        }

        @Test
        @DisplayName("rejects multipart initiate when status is not UPLOADING")
        void rejectsWhenNotUploading() {
            ContentItem item = TestDataFactory.processingItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));

            assertThatThrownBy(() -> uploadService.initiateMultipartUpload(
                    CONTENT_ID, TestDataFactory.multipartRequest(3), INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadStateException.class);
        }
    }

    // ── abortUpload ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("abortUpload")
    class AbortUpload {

        @Test
        @DisplayName("happy path — cleans up storage and deletes DB record")
        void happyPath() {
            ContentItem item = TestDataFactory.uploadingVideoItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);
            item.setUploadId("upload-id-to-abort");

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));

            uploadService.abortUpload(CONTENT_ID, INSTRUCTOR_ID);

            verify(storageService).abortMultipartUpload(anyString(), anyString(), eq("upload-id-to-abort"));
            verify(storageService).deleteObject(anyString(), anyString());
            verify(contentRepository).delete(item);
        }

        @Test
        @DisplayName("cannot abort a PROCESSING item")
        void cannotAbortProcessing() {
            ContentItem item = TestDataFactory.processingItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));

            assertThatThrownBy(() -> uploadService.abortUpload(CONTENT_ID, INSTRUCTOR_ID))
                    .isInstanceOf(InvalidUploadStateException.class)
                    .hasMessageContaining("PROCESSING");

            verifyNoInteractions(storageService);
        }

        @Test
        @DisplayName("abort without multipart does not call abortMultipartUpload")
        void abortSingleUpload() {
            ContentItem item = TestDataFactory.uploadingVideoItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);
            // uploadId is null — single file upload, no multipart session

            when(contentRepository.findByIdAndInstructorId(CONTENT_ID, INSTRUCTOR_ID))
                    .thenReturn(Optional.of(item));

            uploadService.abortUpload(CONTENT_ID, INSTRUCTOR_ID);

            verify(storageService, never()).abortMultipartUpload(any(), any(), any());
            verify(storageService).deleteObject(anyString(), anyString());
            verify(contentRepository).delete(item);
        }
    }

    // ── getStatus ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getStatus")
    class GetStatus {

        @Test
        @DisplayName("returns correct status and cdnUrl for READY item")
        void returnsReadyStatus() {
            ContentItem item = TestDataFactory.readyItem(INSTRUCTOR_ID);
            item.setId(CONTENT_ID);

            when(contentRepository.findById(CONTENT_ID)).thenReturn(Optional.of(item));

            ContentStatusResponse res = uploadService.getStatus(CONTENT_ID, INSTRUCTOR_ID);

            assertThat(res.getStatus()).isEqualTo(ContentStatus.READY);
            assertThat(res.getCdnUrl()).isNotBlank();
        }

        @Test
        @DisplayName("throws ContentNotFoundException for unknown id")
        void throwsForUnknown() {
            when(contentRepository.findById(any())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> uploadService.getStatus(UUID.randomUUID(), INSTRUCTOR_ID))
                    .isInstanceOf(ContentNotFoundException.class);
        }
    }
}
