package com.lms.content.service;

import com.lms.content.domain.entity.ContentItem;
import com.lms.content.domain.enums.ContentStatus;
import com.lms.content.domain.enums.ContentType;
import com.lms.content.dto.request.ContentUploadRequest;
import com.lms.content.dto.response.ContentItemResponse;
import com.lms.content.dto.response.SignedUrlResponse;
import com.lms.content.exception.ContentNotFoundException;
import com.lms.content.exception.OwnershipException;
import com.lms.content.mapper.ContentItemMapper;
import com.lms.content.messaging.ContentEventPublisher;
import com.lms.content.repository.ContentItemRepository;
import com.lms.content.service.impl.ContentServiceImpl;
import com.lms.content.service.storage.StorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ContentService Unit Tests")
class ContentServiceTest {

    @Mock private ContentItemRepository contentRepository;
    @Mock private StorageService storageService;
    @Mock private ContentItemMapper contentMapper;
    @Mock private ContentEventPublisher eventPublisher;

    @InjectMocks
    private ContentServiceImpl contentService;

    private final UUID instructorId = UUID.randomUUID();
    private final UUID lectureId    = UUID.randomUUID();
    private final UUID courseId     = UUID.randomUUID();
    private final UUID contentId    = UUID.randomUUID();

    @BeforeEach
    void setup() {
        // Inject @Value fields not set by Mockito
        ReflectionTestUtils.setField(contentService, "signedUrlExpiry", 3600L);
    }

    // ─── Upload ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("upload()")
    class UploadTests {

        @Test
        @DisplayName("should persist record as UPLOADING and return response immediately")
        void upload_persistsAsUploading() {
            // Arrange
            MockMultipartFile file = new MockMultipartFile(
                "file", "video.mp4", "video/mp4", new byte[100]);

            ContentUploadRequest request = new ContentUploadRequest();
            request.setLectureId(lectureId);
            request.setCourseId(courseId);
            request.setContentType(ContentType.VIDEO);
            request.setFile(file);

            ContentItem saved = ContentItem.builder()
                .id(contentId)
                .lectureId(lectureId)
                .courseId(courseId)
                .instructorId(instructorId)
                .contentType(ContentType.VIDEO)
                .status(ContentStatus.UPLOADING)
                .storageKey("pending")
                .mimeType("video/mp4")
                .originalFilename("video.mp4")
                .build();

            ContentItemResponse expectedResponse = ContentItemResponse.builder()
                .id(contentId)
                .status(ContentStatus.UPLOADING)
                .build();

            when(contentRepository.save(any(ContentItem.class))).thenReturn(saved);
            when(contentMapper.toResponse(saved)).thenReturn(expectedResponse);

            // Act
            ContentItemResponse result = contentService.upload(request, instructorId);

            // Assert
            assertThat(result.getId()).isEqualTo(contentId);
            assertThat(result.getStatus()).isEqualTo(ContentStatus.UPLOADING);
            verify(contentRepository, times(1)).save(any(ContentItem.class));
        }
    }

    // ─── Signed URL ───────────────────────────────────────────────────────

    @Nested
    @DisplayName("generateSignedUrl()")
    class SignedUrlTests {

        @Test
        @DisplayName("owner can generate a signed URL")
        void generateSignedUrl_ownerSucceeds() {
            ContentItem item = readyVideoItem();
            when(contentRepository.findByIdAndStatus(contentId, ContentStatus.READY))
                .thenReturn(Optional.of(item));
            when(storageService.generateSignedUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://signed.url/video.mp4?token=abc");

            SignedUrlResponse response =
                contentService.generateSignedUrl(contentId, instructorId, false);

            assertThat(response.getSignedUrl()).contains("signed.url");
            assertThat(response.getTtlSeconds()).isEqualTo(3600L);
        }

        @Test
        @DisplayName("admin can access any content")
        void generateSignedUrl_adminSucceeds() {
            UUID adminId    = UUID.randomUUID();
            ContentItem item = readyVideoItem();
            when(contentRepository.findByIdAndStatus(contentId, ContentStatus.READY))
                .thenReturn(Optional.of(item));
            when(storageService.generateSignedUrl(anyString(), anyString(), anyLong()))
                .thenReturn("https://signed.url/video.mp4?token=admin");

            assertThatNoException().isThrownBy(() ->
                contentService.generateSignedUrl(contentId, adminId, true));
        }

        @Test
        @DisplayName("non-owner non-admin is denied")
        void generateSignedUrl_nonOwnerDenied() {
            UUID otherUser = UUID.randomUUID();
            ContentItem item = readyVideoItem();
            when(contentRepository.findByIdAndStatus(contentId, ContentStatus.READY))
                .thenReturn(Optional.of(item));

            assertThatThrownBy(() ->
                contentService.generateSignedUrl(contentId, otherUser, false))
                .isInstanceOf(OwnershipException.class)
                .hasMessageContaining("Access denied");
        }

        @Test
        @DisplayName("throws ContentNotFoundException when content not found or not ready")
        void generateSignedUrl_notFound() {
            when(contentRepository.findByIdAndStatus(contentId, ContentStatus.READY))
                .thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                contentService.generateSignedUrl(contentId, instructorId, false))
                .isInstanceOf(ContentNotFoundException.class);
        }
    }

    // ─── Delete ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("delete()")
    class DeleteTests {

        @Test
        @DisplayName("owner can delete their content")
        void delete_ownerSucceeds() {
            ContentItem item = readyVideoItem();
            when(contentRepository.findById(contentId)).thenReturn(Optional.of(item));
            when(contentRepository.save(any())).thenReturn(item);
            doNothing().when(eventPublisher).publishContentDeleted(any());

            assertThatNoException().isThrownBy(() ->
                contentService.delete(contentId, instructorId, false));

            assertThat(item.getStatus()).isEqualTo(ContentStatus.DELETED);
            verify(contentRepository).save(item);
        }

        @Test
        @DisplayName("non-owner cannot delete")
        void delete_nonOwnerDenied() {
            UUID otherUser = UUID.randomUUID();
            ContentItem item = readyVideoItem();
            when(contentRepository.findById(contentId)).thenReturn(Optional.of(item));

            assertThatThrownBy(() ->
                contentService.delete(contentId, otherUser, false))
                .isInstanceOf(OwnershipException.class);
        }
    }

    // ─── getLectureContent ────────────────────────────────────────────────

    @Nested
    @DisplayName("getLectureContent()")
    class GetLectureContentTests {

        @Test
        @DisplayName("returns mapped responses for all lecture content")
        void getLectureContent_returnsAll() {
            ContentItem item = readyVideoItem();
            ContentItemResponse mapped = ContentItemResponse.builder().id(contentId).build();

            when(contentRepository.findByLectureId(lectureId)).thenReturn(List.of(item));
            when(contentMapper.toResponse(item)).thenReturn(mapped);

            List<ContentItemResponse> result =
                contentService.getLectureContent(lectureId, instructorId, false);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getId()).isEqualTo(contentId);
        }
    }

    // ─── Helpers ──────────────────────────────────────────────────────────

    private ContentItem readyVideoItem() {
        return ContentItem.builder()
            .id(contentId)
            .lectureId(lectureId)
            .courseId(courseId)
            .instructorId(instructorId)
            .contentType(ContentType.VIDEO)
            .status(ContentStatus.READY)
            .storageKey("videos/abc/video.mp4")
            .mimeType("video/mp4")
            .originalFilename("lecture.mp4")
            .build();
    }
}
