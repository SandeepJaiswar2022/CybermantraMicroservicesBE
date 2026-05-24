package com.mobisec.in.contentservice.mapper;

import com.mobisec.in.contentservice.domain.dto.ContentStatusResponse;
import com.mobisec.in.contentservice.domain.entity.ContentItem;
import org.springframework.stereotype.Component;

@Component
public class ContentMapper {

    public ContentStatusResponse toStatusResponse(ContentItem item) {
        return new ContentStatusResponse(
                item.getId(),
                item.getLectureId(),
                item.getCourseId(),
                item.getContentType().name(),
                item.getStatus().name(),
                item.getOriginalFilename(),
                item.getMimeType(),
                item.getFileSizeBytes(),
                item.getDurationSeconds(),
                item.getWidthPixels(),
                item.getHeightPixels(),
                item.getHlsManifestKey(),
                item.isMultipart(),
                item.getCreatedAt(),
                item.getUpdatedAt()
        );
    }
}
