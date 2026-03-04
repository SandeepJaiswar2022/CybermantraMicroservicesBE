package com.lms.content.domain.enums;

/** Which storage backend holds this file. Stored in DB so we know how to fetch it later. */
public enum StorageProvider {
    CLOUDINARY,
    MINIO,
    /** Placeholder for future CDN-origin integration */
    CDN
}
