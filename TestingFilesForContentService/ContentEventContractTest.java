package com.example.content.contract;

import com.example.content.config.ContainerConfig;
import com.example.content.config.RabbitMQConfig;
import com.example.content.config.TestDataFactory;
import com.example.content.config.TestJwtFactory;
import com.example.content.dto.request.*;
import com.example.content.dto.response.*;
import com.example.content.entity.*;
import com.example.content.service.UploadService;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.*;

/**
 * LAYER: Contract / Event
 * SCOPE: Verifies that the correct RabbitMQ events fire with the right payload
 *        after upload completes.
 *
 * Uses a test listener that captures messages and Awaitility to wait
 * for async message delivery.
 */
@SpringBootTest
@ActiveProfiles("test")
@ContextConfiguration(initializers = ContainerConfig.Initializer.class)
@Import(ContentEventContractTest.TestMessageCaptor.class)
@DisplayName("ContentEvent — contract tests (real RabbitMQ)")
class ContentEventContractTest extends ContainerConfig {

    @Autowired UploadService     uploadService;
    @Autowired TestMessageCaptor captor;

    private static final UUID INSTRUCTOR_ID = TestJwtFactory.INSTRUCTOR_ID;

    @BeforeEach
    void clearCaptor() {
        captor.clear();
    }

    // ── CONTENT_UPLOADED event ────────────────────────────────────────────────

    @Test
    @DisplayName("CONTENT_UPLOADED event fires with correct payload after /complete")
    void contentUploadedEventFires() throws Exception {
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        String eTag = putToMinIO(res.getPresignedUrl(), "video".getBytes(), "video/mp4");
        uploadService.completeUpload(res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()), INSTRUCTOR_ID);

        // Wait for message to arrive (async delivery)
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> captor.uploadedEvents().stream()
                        .anyMatch(e -> e.get("contentId").equals(res.getContentId().toString())));

        Map<String, Object> event = captor.uploadedEvents().stream()
                .filter(e -> e.get("contentId").equals(res.getContentId().toString()))
                .findFirst().orElseThrow();

        assertThat(event.get("eventType")).isEqualTo("CONTENT_UPLOADED");
        assertThat(event.get("contentId")).isEqualTo(res.getContentId().toString());
        assertThat(event.get("lectureId")).isEqualTo(TestDataFactory.LECTURE_ID.toString());
        assertThat(event.get("courseId")).isEqualTo(TestDataFactory.COURSE_ID.toString());
        assertThat(event.get("instructorId")).isEqualTo(INSTRUCTOR_ID.toString());
        assertThat(event.get("contentType")).isEqualTo("VIDEO");
        assertThat(event.get("mimeType")).isEqualTo("video/mp4");
        assertThat(event.get("storageKey")).isEqualTo(res.getStorageKey());
        assertThat((Long) event.get("fileSizeBytes")).isPositive();
    }

    // ── CONTENT_READY event ───────────────────────────────────────────────────

    @Test
    @DisplayName("CONTENT_READY event fires with cdnUrl after skip-transcode promotion")
    void contentReadyEventFires() throws Exception {
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.pdfInitiateRequest(), INSTRUCTOR_ID);

        String eTag = putToMinIO(res.getPresignedUrl(), "PDF content".getBytes(), "application/pdf");
        uploadService.completeUpload(res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()), INSTRUCTOR_ID);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> captor.readyEvents().stream()
                        .anyMatch(e -> e.get("contentId").equals(res.getContentId().toString())));

        Map<String, Object> event = captor.readyEvents().stream()
                .filter(e -> e.get("contentId").equals(res.getContentId().toString()))
                .findFirst().orElseThrow();

        assertThat(event.get("eventType")).isEqualTo("CONTENT_READY");
        assertThat(event.get("cdnUrl")).isNotNull().isInstanceOf(String.class);
        assertThat((String) event.get("cdnUrl")).isNotBlank();
        assertThat(event.get("lectureId")).isEqualTo(TestDataFactory.LECTURE_ID.toString());
        assertThat(event.get("courseId")).isEqualTo(TestDataFactory.COURSE_ID.toString());
    }

    @Test
    @DisplayName("both UPLOADED and READY events fire for a single complete call (skip-transcode)")
    void bothEventsFireForSkipTranscode() throws Exception {
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.videoInitiateRequest(), INSTRUCTOR_ID);

        String eTag = putToMinIO(res.getPresignedUrl(), "video".getBytes(), "video/mp4");
        uploadService.completeUpload(res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()), INSTRUCTOR_ID);

        String cid = res.getContentId().toString();

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> {
                    assertThat(captor.uploadedEvents().stream()
                            .anyMatch(e -> e.get("contentId").equals(cid))).isTrue();
                    assertThat(captor.readyEvents().stream()
                            .anyMatch(e -> e.get("contentId").equals(cid))).isTrue();
                });
    }

    @Test
    @DisplayName("event payload fileSizeBytes matches actual uploaded size")
    void fileSizeBytesIsAccurate() throws Exception {
        byte[] fileBytes = "exactly 21 bytes file".getBytes(); // 21 bytes
        InitiateUploadResponse res = uploadService.initiateUpload(
                TestDataFactory.pdfInitiateRequest(), INSTRUCTOR_ID);

        String eTag = putToMinIO(res.getPresignedUrl(), fileBytes, "application/pdf");
        uploadService.completeUpload(res.getContentId(),
                TestDataFactory.completeRequest(eTag, res.getStorageKey()), INSTRUCTOR_ID);

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .until(() -> captor.readyEvents().stream()
                        .anyMatch(e -> e.get("contentId").equals(res.getContentId().toString())));

        Map<String, Object> event = captor.readyEvents().stream()
                .filter(e -> e.get("contentId").equals(res.getContentId().toString()))
                .findFirst().orElseThrow();

        assertThat((Long) event.get("fileSizeBytes")).isEqualTo(fileBytes.length);
    }

    // ── Test listener component ───────────────────────────────────────────────

    /**
     * Lightweight in-process consumer that captures events for assertions.
     * Bound to the same queues the real services would consume from.
     */
    @TestComponent
    static class TestMessageCaptor {

        private final List<Map<String, Object>> uploadedEvents = new CopyOnWriteArrayList<>();
        private final List<Map<String, Object>> readyEvents    = new CopyOnWriteArrayList<>();

        @RabbitListener(queues = RabbitMQConfig.Q_TRANSCODE)
        void captureUploaded(Map<String, Object> event) {
            uploadedEvents.add(event);
        }

        @RabbitListener(queues = RabbitMQConfig.Q_COURSE_CONTENT)
        void captureReady(Map<String, Object> event) {
            String type = (String) event.get("eventType");
            if ("CONTENT_READY".equals(type)) readyEvents.add(event);
        }

        List<Map<String, Object>> uploadedEvents() { return uploadedEvents; }
        List<Map<String, Object>> readyEvents()    { return readyEvents; }

        void clear() {
            uploadedEvents.clear();
            readyEvents.clear();
        }
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
        assertThat(res.statusCode()).isEqualTo(200);
        return res.headers().firstValue("ETag").orElseThrow();
    }
}
