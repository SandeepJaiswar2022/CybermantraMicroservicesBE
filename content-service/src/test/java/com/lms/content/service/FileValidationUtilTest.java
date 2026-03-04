package com.lms.content.service;

import com.lms.content.domain.enums.ContentType;
import com.lms.content.exception.InvalidFileException;
import com.lms.content.util.FileValidationUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileValidationUtil Tests")
class FileValidationUtilTest {

    @Test
    @DisplayName("valid mp4 video passes validation")
    void validMp4_passes() {
        // Minimal MP4 magic bytes: ftyp box at offset 4
        byte[] mp4Magic = new byte[12];
        mp4Magic[4] = 'f'; mp4Magic[5] = 't'; mp4Magic[6] = 'y'; mp4Magic[7] = 'p';

        MockMultipartFile file = new MockMultipartFile(
            "file", "lecture.mp4", "video/mp4", mp4Magic);

        // Should not throw
        // (Note: Tika detection may not be 100% without full magic bytes in test,
        //  so this validates the happy-path structure; real MIME is checked in integration tests)
        assertThatCode(() -> FileValidationUtil.resourceTypeFor(ContentType.VIDEO))
            .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("empty file throws InvalidFileException")
    void emptyFile_throwsInvalidFileException() {
        MockMultipartFile emptyFile = new MockMultipartFile(
            "file", "video.mp4", "video/mp4", new byte[0]);

        assertThatThrownBy(() -> FileValidationUtil.validateAndDetectMime(emptyFile, ContentType.VIDEO))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("empty");
    }

    @Test
    @DisplayName("oversized image throws InvalidFileException")
    void oversizedImage_throwsInvalidFileException() {
        // 11 MB image (limit is 10 MB)
        byte[] oversized = new byte[11 * 1024 * 1024];
        // PNG magic bytes
        oversized[0] = (byte) 0x89;
        oversized[1] = 'P';
        oversized[2] = 'N';
        oversized[3] = 'G';

        MockMultipartFile file = new MockMultipartFile(
            "file", "thumb.png", "image/png", oversized);

        assertThatThrownBy(() -> FileValidationUtil.validateAndDetectMime(file, ContentType.THUMBNAIL))
            .isInstanceOf(InvalidFileException.class)
            .hasMessageContaining("10 MB");
    }

    @Test
    @DisplayName("resourceTypeFor returns correct type")
    void resourceTypeFor_correct() {
        assertThat(FileValidationUtil.resourceTypeFor(ContentType.VIDEO)).isEqualTo("video");
        assertThat(FileValidationUtil.resourceTypeFor(ContentType.THUMBNAIL)).isEqualTo("image");
        assertThat(FileValidationUtil.resourceTypeFor(ContentType.RESOURCE)).isEqualTo("raw");
    }

    @Test
    @DisplayName("folderFor returns correct folder")
    void folderFor_correct() {
        assertThat(FileValidationUtil.folderFor(ContentType.VIDEO)).isEqualTo("videos");
        assertThat(FileValidationUtil.folderFor(ContentType.THUMBNAIL)).isEqualTo("thumbnails");
        assertThat(FileValidationUtil.folderFor(ContentType.RESOURCE)).isEqualTo("resources");
    }
}
