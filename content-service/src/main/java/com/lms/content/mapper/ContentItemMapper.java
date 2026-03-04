package com.lms.content.mapper;

import com.lms.content.domain.entity.ContentItem;
import com.lms.content.dto.response.ContentItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

/**
 * MapStruct mapper for ContentItem → ContentItemResponse.
 *
 * <p>MapStruct generates the implementation at compile time (zero reflection overhead at runtime).
 * We explicitly exclude {@code storageKey} — it must never appear in API responses.
 *
 * <p>{@code signedUrl} and {@code signedUrlExpiresAt} are not mapped here;
 * they are set separately when generating signed URLs.
 */
@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ContentItemMapper {

    @Mapping(target = "signedUrl", ignore = true)
    @Mapping(target = "signedUrlExpiresAt", ignore = true)
    ContentItemResponse toResponse(ContentItem entity);
}
