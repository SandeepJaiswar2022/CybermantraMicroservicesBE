package com.lms.content.controller;

import com.lms.content.domain.enums.ContentStatus;
import com.lms.content.dto.response.ContentItemResponse;
import com.lms.content.dto.response.SignedUrlResponse;
import com.lms.content.service.ContentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web layer (controller) tests focusing on security and HTTP contract.
 *
 * <p>Uses {@code @WebMvcTest} to load only the web layer (no DB, no storage).
 * Service layer is mocked via {@code @MockBean}.
 */
@WebMvcTest(ContentController.class)
@DisplayName("ContentController Security Tests")
class ContentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentService contentService;

    private final UUID contentId    = UUID.randomUUID();
    private final UUID lectureId    = UUID.randomUUID();
    private final UUID instructorId = UUID.randomUUID();

    // ─── Unauthenticated access ───────────────────────────────────────────

    @Test
    @DisplayName("GET /signed-url without JWT returns 401")
    void signedUrl_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/content/{id}/signed-url", contentId))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE without JWT returns 401")
    void delete_unauthenticated_returns401() throws Exception {
        mockMvc.perform(delete("/content/{id}", contentId))
            .andExpect(status().isUnauthorized());
    }

    // ─── Role-based access ────────────────────────────────────────────────

    @Test
    @DisplayName("STUDENT cannot upload (403)")
    void upload_studentRole_returns403() throws Exception {
        mockMvc.perform(
            multipart("/content/upload")
                .file("file", "data".getBytes())
                .param("lectureId", lectureId.toString())
                .param("courseId", UUID.randomUUID().toString())
                .param("contentType", "VIDEO")
                .with(jwt().jwt(j -> j
                    .subject(instructorId.toString())
                    .claim("roles", List.of("STUDENT"))))
        ).andExpect(status().isForbidden());
    }

    // ─── Signed URL ───────────────────────────────────────────────────────

    @Test
    @DisplayName("Authenticated user gets signed URL")
    void signedUrl_authenticated_returns200() throws Exception {
        SignedUrlResponse response = SignedUrlResponse.builder()
            .contentId(contentId)
            .signedUrl("https://cdn.example.com/video?sig=abc")
            .expiresAt(9999999L)
            .ttlSeconds(3600L)
            .build();

        when(contentService.generateSignedUrl(eq(contentId), eq(instructorId), eq(false)))
            .thenReturn(response);

        mockMvc.perform(
            get("/content/{id}/signed-url", contentId)
                .with(jwt().jwt(j -> j
                    .subject(instructorId.toString())
                    .claim("roles", List.of("INSTRUCTOR"))))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.signedUrl").value("https://cdn.example.com/video?sig=abc"))
        .andExpect(jsonPath("$.success").value(true));
    }

    // ─── Lecture content ──────────────────────────────────────────────────

    @Test
    @DisplayName("GET /lecture/{id} returns content list")
    void getLectureContent_returns200() throws Exception {
        ContentItemResponse item = ContentItemResponse.builder()
            .id(contentId)
            .status(ContentStatus.READY)
            .build();

        when(contentService.getLectureContent(eq(lectureId), any(), eq(false)))
            .thenReturn(List.of(item));

        mockMvc.perform(
            get("/content/lecture/{id}", lectureId)
                .with(jwt().jwt(j -> j
                    .subject(instructorId.toString())
                    .claim("roles", List.of("INSTRUCTOR"))))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value(contentId.toString()));
    }

    // ─── Delete ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("Instructor can delete their content")
    void delete_instructor_returns200() throws Exception {
        mockMvc.perform(
            delete("/content/{id}", contentId)
                .with(jwt().jwt(j -> j
                    .subject(instructorId.toString())
                    .claim("roles", List.of("INSTRUCTOR"))))
        )
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
    }
}
