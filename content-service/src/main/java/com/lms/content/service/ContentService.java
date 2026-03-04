package com.lms.content.service;

import com.lms.content.domain.enums.ContentType;
import com.lms.content.dto.request.ContentUploadRequest;
import com.lms.content.dto.response.ContentItemResponse;
import com.lms.content.dto.response.SignedUrlResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Content Service contract.
 *
 * <p>Defines the business operations available on content items.
 * The implementation is {@link impl.ContentServiceImpl}.
 */
public interface ContentService {

    /**
     * Upload a file and persist metadata.
     *
     * @param request    upload parameters including file and association IDs
     * @param uploaderId ID of the user performing the upload (from JWT)
     * @return full content item response with metadata (no signed URL at this point)
     */
    ContentItemResponse upload(ContentUploadRequest request, UUID uploaderId);

    /**
     * Generate a time-limited signed URL for accessing a content item.
     *
     * <p>Only accessible to:
     * <ul>
     *   <li>The owning instructor</li>
     *   <li>Enrolled students (authorization delegated to caller — check enrollment
     *       in API Gateway or via event-driven enrollment cache)</li>
     *   <li>Admins</li>
     * </ul>
     *
     * @param contentId  ID of the content item
     * @param requesterId ID of the user requesting access
     * @param isAdmin    whether the requester has admin role
     */
    SignedUrlResponse generateSignedUrl(UUID contentId, UUID requesterId, boolean isAdmin);

    /**
     * Retrieve all content items for a lecture.
     *
     * @param lectureId  the lecture
     * @param requesterId the requesting user (for ownership check)
     * @param isAdmin    admin override
     */
    List<ContentItemResponse> getLectureContent(UUID lectureId, UUID requesterId, boolean isAdmin);

    /**
     * Get paginated content list for an instructor.
     */
    Page<ContentItemResponse> getInstructorContent(UUID instructorId, ContentType contentType, Pageable pageable);

    /**
     * Delete a content item (soft delete + async storage cleanup).
     *
     * @param contentId   ID of the item to delete
     * @param requesterId the requesting user
     * @param isAdmin     admin override
     */
    void delete(UUID contentId, UUID requesterId, boolean isAdmin);

    /**
     * Extension point: called by video transcoding service when processing completes.
     * Updates status to READY and stores HLS manifest key.
     *
     * @param contentId      ID of the content item
     * @param hlsManifestKey storage key of the generated HLS manifest
     */
    void onTranscodeComplete(UUID contentId, String hlsManifestKey);
}
