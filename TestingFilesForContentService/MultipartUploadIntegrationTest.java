package com.example.content.integration;

import com.example.content.config.ContainerConfig;
import com.example.content.config.TestDataFactory;
import com.example.content.config.TestJwtFactory;
import com.example.content.dto.request.*;
import com.example.content.dto.response.*;
import com.example.content.entity.*;
import com.example.content.exception.InvalidUploadStateException;
import com.example.content.repository.ContentItemRepository;
import com.example.content.service.UploadService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.net.URI;
import java.net.http.*;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * LAYER: Integration
 * SCOPE: Multipart (chunked) upload path with real MinIO
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@ContextConfiguration(initializers = ContainerConfig.Initializer.class)
@DisplayName("Multipart upload — integration tests")
class MultipartUploadIntegrationTest extends ContainerConfig {

    @Autowired UploadService         uploadService;
    @Autowired ContentItemRepository contentRepository;

    private static final UUID INSTRUCTOR_ID = TestJwtFactory.INSTRUCTOR_ID;

    @Test
    @DisplayName("full multipart flow: initiate → multipart/initiate → PUT parts → complete → READY")
    void fullMultipartFlow() throws Exception {
        // Step 1: initiate (creates contentId + storageKey)
        InitiateUploadResponse initRes = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);
        UUID contentId = initRes.getContentId();

        // Step 2: initiate multipart — 3 parts
        InitiateMultipartResponse mpRes = uploadService.initiateMultipartUpload(
                contentId, TestDataFactory.multipartRequest(3), INSTRUCTOR_ID);

        assertThat(mpRes.getUploadId()).isNotBlank();
        assertThat(mpRes.getPresignedUrls()).hasSize(3);

        // Verify uploadId is persisted in DB
        ContentItem item = contentRepository.findById(contentId).orElseThrow();
        assertThat(item.getUploadId()).isEqualTo(mpRes.getUploadId());

        // Step 3: PUT each part (MinIO requires each part >= 5 MB except the last)
        // In tests we use small parts — MinIO allows <5MB for the last part
        // For a real test at scale you'd use 5MB+ parts
        List<CompleteMultipartRequest.PartInfo> parts = new ArrayList<>();
        List<String> urls = mpRes.getPresignedUrls();

        for (int i = 0; i < urls.size(); i++) {
            byte[] chunk = ("chunk-content-part-" + (i + 1)).getBytes();
            String eTag  = putPart(urls.get(i), chunk);

            CompleteMultipartRequest.PartInfo part = new CompleteMultipartRequest.PartInfo();
            part.setPartNumber(i + 1);
            part.setETag(eTag);
            parts.add(part);
        }

        // Step 4: complete multipart
        CompleteMultipartRequest completeReq =
                TestDataFactory.completeMultipartRequest(mpRes.getUploadId(), parts);
        uploadService.completeMultipartUpload(contentId, completeReq, INSTRUCTOR_ID);

        // Step 5: assert READY
        ContentItem finalItem = contentRepository.findById(contentId).orElseThrow();
        assertThat(finalItem.getStatus()).isEqualTo(ContentStatus.READY);
        assertThat(finalItem.getCdnUrl()).isNotBlank();
        assertThat(finalItem.getFileSizeBytes()).isPositive();
        assertThat(finalItem.getReadyAt()).isNotNull();
    }

    @Test
    @DisplayName("uploadId mismatch in /multipart/complete throws InvalidUploadStateException")
    void uploadIdMismatchThrows() throws Exception {
        InitiateUploadResponse initRes = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        uploadService.initiateMultipartUpload(
                initRes.getContentId(), TestDataFactory.multipartRequest(2), INSTRUCTOR_ID);

        CompleteMultipartRequest req = TestDataFactory.completeMultipartRequest(
                "wrong-upload-id", List.of());

        assertThatThrownBy(() -> uploadService.completeMultipartUpload(
                initRes.getContentId(), req, INSTRUCTOR_ID))
                .isInstanceOf(InvalidUploadStateException.class)
                .hasMessageContaining("uploadId mismatch");
    }

    @Test
    @DisplayName("cannot initiate multipart when status is not UPLOADING")
    void cannotInitiateMultipartAfterProcessing() throws Exception {
        // Get the item to PROCESSING state first
        InitiateUploadResponse initRes = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        // Directly update to processing via the repository for test setup
        ContentItem item = contentRepository.findById(initRes.getContentId()).orElseThrow();
        item.markProcessing("etag");
        contentRepository.save(item);

        assertThatThrownBy(() -> uploadService.initiateMultipartUpload(
                initRes.getContentId(), TestDataFactory.multipartRequest(3), INSTRUCTOR_ID))
                .isInstanceOf(InvalidUploadStateException.class);
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    private String putPart(String presignedPartUrl, byte[] bytes) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpResponse<String> res = client.send(
                HttpRequest.newBuilder()
                        .uri(URI.create(presignedPartUrl))
                        .PUT(HttpRequest.BodyPublishers.ofByteArray(bytes))
                        .header("Content-Type", "video/mp4")
                        .build(),
                HttpResponse.BodyHandlers.ofString()
        );
        assertThat(res.statusCode()).as("Part PUT should return 200").isEqualTo(200);
        return res.headers().firstValue("ETag")
                .orElseThrow(() -> new AssertionError("No ETag in part response"));
    }
}
