package com.lms.content.dto.request;

import com.lms.content.domain.enums.ContentType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Request DTO for content upload.
 * The file is received as a MultipartFile; metadata is in the form fields.
 */
@Data
public class ContentUploadRequest {

    @NotNull(message = "Lecture ID is required")
    private UUID lectureId;

    @NotNull(message = "Course ID is required")
    private UUID courseId;

    @NotNull(message = "Content type is required")
    private ContentType contentType;

    @NotNull(message = "File is required")
    private MultipartFile file;
}
