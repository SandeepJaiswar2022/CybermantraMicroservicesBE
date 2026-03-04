-- V1__create_content_items.sql
-- Content Service: initial schema

CREATE TABLE IF NOT EXISTS content_items (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    lecture_id        UUID        NOT NULL,
    course_id         UUID        NOT NULL,
    instructor_id     UUID        NOT NULL,

    -- Storage
    storage_key       VARCHAR(512) NOT NULL,
    storage_provider  VARCHAR(20)  NOT NULL CHECK (storage_provider IN ('CLOUDINARY', 'MINIO', 'CDN')),

    -- Content classification
    content_type      VARCHAR(20)  NOT NULL CHECK (content_type IN ('VIDEO', 'RESOURCE', 'THUMBNAIL')),
    status            VARCHAR(20)  NOT NULL DEFAULT 'UPLOADING'
                          CHECK (status IN ('UPLOADING', 'READY', 'PROCESSING', 'FAILED', 'DELETED')),

    -- File metadata
    original_filename VARCHAR(255) NOT NULL,
    mime_type         VARCHAR(100) NOT NULL,
    file_size_bytes   BIGINT,
    duration_seconds  INTEGER,
    width_pixels      INTEGER,
    height_pixels     INTEGER,

    -- HLS extension point (null until transcoding is implemented)
    hls_manifest_key  VARCHAR(512),

    -- Audit
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- ─── Indexes ──────────────────────────────────────────────────────────────────
-- Most frequent query pattern: fetch content for a lecture
CREATE INDEX idx_content_lecture_id  ON content_items (lecture_id);

-- Course-level queries (cascade delete, stats)
CREATE INDEX idx_content_course_id   ON content_items (course_id);

-- Instructor dashboard queries
CREATE INDEX idx_content_instructor  ON content_items (instructor_id);

-- Filtered list queries (e.g., only READY videos)
CREATE INDEX idx_content_type_status ON content_items (content_type, status);

-- ─── Auto-update updated_at ──────────────────────────────────────────────────
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_content_items_updated_at
    BEFORE UPDATE ON content_items
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
