package com.lms.content.domain.enums;

/**
 * Lifecycle state of a piece of content.
 *
 * UPLOADING  -> READY  (happy path)
 * UPLOADING  -> FAILED (storage error)
 * READY      -> PROCESSING (video transcoding triggered)
 * PROCESSING -> READY (transcoding complete)
 * READY      -> DELETED
 */
public enum ContentStatus {
    /** Upload initiated; file not yet persisted to storage */
    UPLOADING,
    /** File stored; metadata saved; accessible */
    READY,
    /** Being processed by downstream pipeline (e.g., transcoding) */
    PROCESSING,
    /** Upload or processing failed */
    FAILED,
    /** Logically deleted; storage file may still exist pending cleanup */
    DELETED
}
