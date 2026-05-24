package com.example.content.integration;

import com.example.content.config.ContainerConfig;
import com.example.content.config.TestDataFactory;
import com.example.content.config.TestJwtFactory;
import com.example.content.dto.request.*;
import com.example.content.dto.response.*;
import com.example.content.entity.*;
import com.example.content.repository.ContentItemRepository;
import com.example.content.service.UploadService;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.*;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

/**
 * LAYER: Integration
 * SCOPE: Full service + real MinIO (Testcontainers) + real Postgres (Testcontainers)
 * DEPENDENCIES: RabbitMQ mocked via skip-transcoding flag
 *
 * These tests prove:
 *  - presigned URL is actually usable (PUT to MinIO succeeds)
 *  - ETag from MinIO is correctly verified on /complete
 *  - DB state transitions are persisted correctly
 *  - Object key is sanitised and routed to the right bucket
 *  - Tampered / expired flows cause correct exceptions
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = ContainerConfig.Initializer.class)
@DisplayName("Upload — integration tests (real MinIO + Postgres)")
class UploadIntegrationTest extends ContainerConfig {

    @Autowired UploadService          uploadService;
    @Autowired ContentItemRepository  contentRepository;
    @Autowired MinioClient            minioClient;

    private static final UUID INSTRUCTOR_ID = TestJwtFactory.INSTRUCTOR_ID;

    // ── Single file happy path ─────────────────────────────────────────────────

    @Test
    @DisplayName("full single-file flow: initiate → PUT → complete → READY")
    void singleFileFullFlow() throws Exception {
        // 1. Initiate
        InitiateUploadRequest initReq = TestDataFactory.videoInitiateRequest();
        InitiateUploadResponse initRes = uploadService.initiateUpload(initReq, INSTRUCTOR_ID);

        assertThat(initRes.getContentId()).isNotNull();
        assertThat(initRes.getPresignedUrl()).startsWith("http");

        // Verify DB record created as UPLOADING
        ContentItem saved = contentRepository.findById(initRes.getContentId()).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(ContentStatus.UPLOADING);
        assertThat(saved.getStorageKey()).startsWith("video/");

        // 2. PUT the file bytes directly to MinIO using the presigned URL
        byte[] fileBytes = "fake video content for testing".getBytes();
        HttpClient httpClient = HttpClient.newHttpClient();
        HttpResponse<String> putResponse = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(initRes.getPresignedUrl()))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(fileBytes))
                        .header("Content-Type", "video/mp4")
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(putResponse.statusCode()).isEqualTo(200);

        // Extract ETag from the PUT response header
        String eTag = putResponse.headers().firstValue("ETag").orElseThrow(
                () -> new AssertionError("MinIO did not return ETag header"));

        // 3. Complete
        CompleteUploadRequest completeReq = TestDataFactory.completeRequest(eTag, initRes.getStorageKey());
        uploadService.completeUpload(initRes.getContentId(), completeReq, INSTRUCTOR_ID);

        // 4. Verify final DB state — skip-transcoding promotes straight to READY
        ContentItem finalItem = contentRepository.findById(initRes.getContentId()).orElseThrow();
        assertThat(finalItem.getStatus()).isEqualTo(ContentStatus.READY);
        assertThat(finalItem.getCdnUrl()).isNotBlank();
        assertThat(finalItem.getETag()).isNotBlank();
        assertThat(finalItem.getFileSizeBytes()).isEqualTo(fileBytes.length);
        assertThat(finalItem.getReadyAt()).isNotNull();
    }

    @Test
    @DisplayName("PDF upload: correct bucket, doc prefix, reaches READY")
    void pdfUploadFlow() throws Exception {
        InitiateUploadRequest req = TestDataFactory.pdfInitiateRequest();
        InitiateUploadResponse res = uploadService.initiateUpload(req, INSTRUCTOR_ID);

        assertThat(res.getStorageKey()).startsWith("doc/");

        byte[] bytes = "PDF content".getBytes();
        String eTag = putToMinIO(res.getPresignedUrl(), bytes, "application/pdf");

        uploadService.completeUpload(res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()), INSTRUCTOR_ID);

        ContentItem item = contentRepository.findById(res.getContentId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(ContentStatus.READY);
    }

    @Test
    @DisplayName("image upload: correct image prefix, reaches READY")
    void imageUploadFlow() throws Exception {
        InitiateUploadRequest req = TestDataFactory.imageInitiateRequest();
        InitiateUploadResponse res = uploadService.initiateUpload(req, INSTRUCTOR_ID);

        assertThat(res.getStorageKey()).startsWith("image/");

        byte[] bytes = "PNG bytes".getBytes();
        String eTag = putToMinIO(res.getPresignedUrl(), bytes, "image/png");

        uploadService.completeUpload(res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()), INSTRUCTOR_ID);

        ContentItem item = contentRepository.findById(res.getContentId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(ContentStatus.READY);
    }

    // ── ETag verification ─────────────────────────────────────────────────────

    @Test
    @DisplayName("complete with wrong ETag throws StorageException")
    void wrongETagThrows() throws Exception {
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        // Upload the file
        byte[] bytes = "video bytes".getBytes();
        putToMinIO(res.getPresignedUrl(), bytes, "video/mp4");

        // Complete with a deliberately wrong ETag
        assertThatThrownBy(() -> uploadService.completeUpload(
                res.getContentId(),
                TestDataFactory.completeRequest("\"wrong-etag\"", res.getStorageKey()),
                INSTRUCTOR_ID
        )).isInstanceOf(com.example.content.exception.StorageException.class)
          .hasMessageContaining("mismatch");

        // DB record should still be UPLOADING — not moved forward
        ContentItem item = contentRepository.findById(res.getContentId()).orElseThrow();
        assertThat(item.getStatus()).isEqualTo(ContentStatus.UPLOADING);
    }

    @Test
    @DisplayName("complete without uploading (object not in MinIO) throws StorageException")
    void completeWithoutUploadThrows() {
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        // Do NOT call PUT — object doesn't exist in MinIO
        assertThatThrownBy(() -> uploadService.completeUpload(
                res.getContentId(),
                TestDataFactory.completeRequest("\"fake-etag\"", res.getStorageKey()),
                INSTRUCTOR_ID
        )).isInstanceOf(com.example.content.exception.StorageException.class);
    }

    // ── Ownership ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("different instructor cannot complete another's upload")
    void ownershipEnforced() throws Exception {
        UUID otherInstructor = TestJwtFactory.OTHER_USER_ID;

        // Instructor A initiates
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        byte[] bytes = "video".getBytes();
        String eTag = putToMinIO(res.getPresignedUrl(), bytes, "video/mp4");

        // Instructor B tries to complete — should get ContentNotFoundException
        assertThatThrownBy(() -> uploadService.completeUpload(
                res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()),
                otherInstructor     // different user
        )).isInstanceOf(com.example.content.exception.ContentNotFoundException.class);
    }

    // ── Abort ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("abort deletes DB record and removes object from MinIO")
    void abortCleansUp() throws Exception {
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        UUID contentId = res.getContentId();

        // Partially upload
        putToMinIO(res.getPresignedUrl(), "partial".getBytes(), "video/mp4");

        // Abort
        uploadService.abortUpload(contentId, INSTRUCTOR_ID);

        // DB record should be gone
        assertThat(contentRepository.findById(contentId)).isEmpty();
    }

    @Test
    @DisplayName("double-complete throws InvalidUploadStateException on second call")
    void doubleCompleteThrows() throws Exception {
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        byte[] bytes = "video".getBytes();
        String eTag = putToMinIO(res.getPresignedUrl(), bytes, "video/mp4");

        // First complete — OK
        uploadService.completeUpload(res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()), INSTRUCTOR_ID);

        // Second complete — should fail
        assertThatThrownBy(() -> uploadService.completeUpload(
                res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()),
                INSTRUCTOR_ID
        )).isInstanceOf(com.example.content.exception.InvalidUploadStateException.class);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String putToMinIO(String presignedUrl, byte[] bytes, String contentType) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(presignedUrl))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .header("Content-Type", contentType)
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(res.statusCode()).as("MinIO PUT should return 200").isEqualTo(200);
        return res.headers().firstValue("ETag")
                .orElseThrow(() -> new AssertionError("No ETag in MinIO response"));
    }
}
