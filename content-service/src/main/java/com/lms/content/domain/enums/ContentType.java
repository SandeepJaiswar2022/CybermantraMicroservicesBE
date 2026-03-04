package com.lms.content.domain.enums;

/**
 * Type of content stored by the Content Service.
 * Drives bucket/folder selection, MIME validation, and URL generation strategy.
 */
public enum ContentType {
    /** Lecture video (mp4, webm, mov) */
    VIDEO,
    /** Downloadable lecture attachment (PDF, zip, docx) */
    RESOURCE,
    /** Course or lecture thumbnail image */
    THUMBNAIL
}
