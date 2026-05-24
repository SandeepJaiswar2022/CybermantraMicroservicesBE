package com.example.content.api;

import com.example.content.config.TestDataFactory;
import com.example.content.config.TestJwtFactory;
import com.example.content.dto.request.*;
import com.example.content.dto.response.*;
import com.example.content.entity.*;
import com.example.content.exception.*;
import com.example.content.service.UploadService;
import com.example.content.sse.ContentSseService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * LAYER: API / Controller slice
 * SCOPE: HTTP request → response mapping, security, validation
 * DEPENDENCIES: UploadService + ContentSseService mocked
 *
 * @WebMvcTest loads only the web layer — no DB, no MinIO, no RabbitMQ.
 * Fast feedback on routing, request body validation, HTTP status codes,
 * and security (roles, missing tokens).
 */
@WebMvcTest
@DisplayName("UploadController — API slice tests")
class UploadControllerApiTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper mapper;

    @MockBean UploadService     uploadService;
    @MockBean ContentSseService sseService;

    private static final UUID CONTENT_ID   = UUID.randomUUID();
    private static final UUID INSTRUCTOR_ID = TestJwtFactory.INSTRUCTOR_ID;

    // ── POST /upload/initiate ─────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/content/upload/initiate")
    class InitiateUpload {

        @Test
        @DisplayName("200 — instructor with valid request")
        void happyPath() throws Exception {
            InitiateUploadResponse response = InitiateUploadResponse.builder()
                    .contentId(CONTENT_ID)
                    .presignedUrl("https://minio/presigned")
                    .storageKey("video/uuid/file.mp4")
                    .expiresInMinutes(15)
                    .build();

            when(uploadService.initiateUpload(any(), any())).thenReturn(response);

            mockMvc.perform(post("/api/content/upload/initiate")
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(TestDataFactory.videoInitiateRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.contentId").value(CONTENT_ID.toString()))
                    .andExpect(jsonPath("$.data.presignedUrl").value("https://minio/presigned"))
                    .andExpect(jsonPath("$.data.storageKey").value("video/uuid/file.mp4"))
                    .andExpect(jsonPath("$.data.expiresInMinutes").value(15));
        }

        @Test
        @DisplayName("401 — no JWT token")
        void unauthorisedWithoutToken() throws Exception {
            mockMvc.perform(post("/api/content/upload/initiate")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(TestDataFactory.videoInitiateRequest())))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("403 — STUDENT role cannot initiate upload")
        void studentCannotUpload() throws Exception {
            mockMvc.perform(post("/api/content/upload/initiate")
                            .with(jwt().jwt(TestJwtFactory.student())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(TestDataFactory.videoInitiateRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("400 — missing required fields fails @Valid")
        void missingFieldsFailsValidation() throws Exception {
            String emptyBody = "{}";

            mockMvc.perform(post("/api/content/upload/initiate")
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(emptyBody))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false));
        }

        @Test
        @DisplayName("409 — invalid MIME type returns conflict")
        void invalidMimeReturnsConflict() throws Exception {
            when(uploadService.initiateUpload(any(), any()))
                    .thenThrow(new InvalidUploadStateException("MIME type not allowed"));

            mockMvc.perform(post("/api/content/upload/initiate")
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(TestDataFactory.invalidMimeRequest())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value("MIME type not allowed"));
        }

        @Test
        @DisplayName("ADMIN role can also initiate upload")
        void adminCanUpload() throws Exception {
            when(uploadService.initiateUpload(any(), any()))
                    .thenReturn(InitiateUploadResponse.builder()
                            .contentId(CONTENT_ID).presignedUrl("url")
                            .storageKey("key").expiresInMinutes(15).build());

            mockMvc.perform(post("/api/content/upload/initiate")
                            .with(jwt().jwt(TestJwtFactory.admin())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(TestDataFactory.videoInitiateRequest())))
                    .andExpect(status().isOk());
        }
    }

    // ── POST /{id}/complete ───────────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/content/{id}/complete")
    class CompleteUpload {

        @Test
        @DisplayName("200 — happy path")
        void happyPath() throws Exception {
            doNothing().when(uploadService).completeUpload(any(), any(), any());

            mockMvc.perform(post("/api/content/{id}/complete", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(
                                    TestDataFactory.completeRequest("\"abc123\"", "video/uuid/file.mp4"))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("409 — tampered storageKey")
        void tamperedStorageKey() throws Exception {
            doThrow(new InvalidUploadStateException("storageKey mismatch"))
                    .when(uploadService).completeUpload(any(), any(), any());

            mockMvc.perform(post("/api/content/{id}/complete", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(TestDataFactory.tamperedStorageKey())))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("404 — content not found or not owned by caller")
        void notFound() throws Exception {
            doThrow(new ContentNotFoundException("not found"))
                    .when(uploadService).completeUpload(any(), any(), any());

            mockMvc.perform(post("/api/content/{id}/complete", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(
                                    TestDataFactory.completeRequest("etag", "key"))))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("502 — MinIO unreachable maps to BAD_GATEWAY")
        void minioDown() throws Exception {
            doThrow(new StorageException("MinIO unreachable"))
                    .when(uploadService).completeUpload(any(), any(), any());

            mockMvc.perform(post("/api/content/{id}/complete", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR")))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(
                                    TestDataFactory.completeRequest("etag", "key"))))
                    .andExpect(status().isBadGateway());
        }
    }

    // ── GET /{id}/status ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/content/{id}/status")
    class GetStatus {

        @Test
        @DisplayName("200 — returns READY status with cdnUrl")
        void returnsReadyStatus() throws Exception {
            ContentStatusResponse res = ContentStatusResponse.builder()
                    .contentId(CONTENT_ID)
                    .status(ContentStatus.READY)
                    .contentType(ContentType.VIDEO)
                    .cdnUrl("https://cdn.example.com/video.m3u8")
                    .build();

            when(uploadService.getStatus(eq(CONTENT_ID), any())).thenReturn(res);

            mockMvc.perform(get("/api/content/{id}/status", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("READY"))
                    .andExpect(jsonPath("$.data.cdnUrl").value("https://cdn.example.com/video.m3u8"));
        }

        @Test
        @DisplayName("200 — returns UPLOADING status with null cdnUrl")
        void returnsUploadingStatus() throws Exception {
            ContentStatusResponse res = ContentStatusResponse.builder()
                    .contentId(CONTENT_ID)
                    .status(ContentStatus.UPLOADING)
                    .contentType(ContentType.VIDEO)
                    .cdnUrl(null)
                    .build();

            when(uploadService.getStatus(eq(CONTENT_ID), any())).thenReturn(res);

            mockMvc.perform(get("/api/content/{id}/status", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("UPLOADING"))
                    .andExpect(jsonPath("$.data.cdnUrl").doesNotExist());
        }

        @Test
        @DisplayName("401 — unauthenticated cannot poll status")
        void unauthenticated() throws Exception {
            mockMvc.perform(get("/api/content/{id}/status", CONTENT_ID))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("404 — unknown contentId")
        void unknownId() throws Exception {
            when(uploadService.getStatus(any(), any()))
                    .thenThrow(new ContentNotFoundException("not found"));

            mockMvc.perform(get("/api/content/{id}/status", UUID.randomUUID())
                            .with(jwt().jwt(TestJwtFactory.instructor())))
                    .andExpect(status().isNotFound());
        }
    }

    // ── DELETE /{id} ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("DELETE /api/content/{id}")
    class AbortUpload {

        @Test
        @DisplayName("204 — successful abort returns no content")
        void happyPath() throws Exception {
            doNothing().when(uploadService).abortUpload(any(), any());

            mockMvc.perform(delete("/api/content/{id}", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR"))))
                    .andExpect(status().isNoContent());
        }

        @Test
        @DisplayName("409 — cannot abort PROCESSING item")
        void cannotAbortProcessing() throws Exception {
            doThrow(new InvalidUploadStateException("Cannot abort — current status is PROCESSING"))
                    .when(uploadService).abortUpload(any(), any());

            mockMvc.perform(delete("/api/content/{id}", CONTENT_ID)
                            .with(jwt().jwt(TestJwtFactory.instructor())
                                    .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_INSTRUCTOR"))))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value("Cannot abort — current status is PROCESSING"));
        }
    }
}
