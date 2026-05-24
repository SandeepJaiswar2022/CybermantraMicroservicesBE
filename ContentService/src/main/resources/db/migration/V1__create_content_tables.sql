-- =========================================================
-- V1__create_content_tables.sql
-- =========================================================

-- ── ENUMs ────────────────────────────────────────────────
CREATE TYPE content_type_enum   AS ENUM ('VIDEO', 'RESOURCE', 'THUMBNAIL');
CREATE TYPE content_status_enum AS ENUM ('UPLOADING', 'PROCESSING', 'READY', 'FAILED', 'DELETED');
CREATE TYPE storage_provider_enum AS ENUM ('MINIO');

-- ── content_items ────────────────────────────────────────
CREATE TABLE content_items
(
    id                UUID PRIMARY KEY         DEFAULT gen_random_uuid(),

    -- Ownership
    lecture_id        UUID                NOT NULL,
    course_id         UUID                NOT NULL,
    instructor_id     UUID                NOT NULL,

    -- Storage
    storage_key       VARCHAR(512)        NOT NULL,
    storage_provider  storage_provider_enum NOT NULL DEFAULT 'MINIO',
    content_type      content_type_enum   NOT NULL,

    -- File metadata
    original_filename VARCHAR(255)        NOT NULL,
    mime_type         VARCHAR(100)        NOT NULL,
    file_size_bytes   BIGINT,
    duration_seconds  INTEGER,
    width_pixels      INTEGER,
    height_pixels     INTEGER,

    -- HLS extension point (populated by transcoding service)
    hls_manifest_key  VARCHAR(512),

    -- Multipart upload tracking (NULL = single-part upload)
    minio_upload_id   VARCHAR(255),

    -- Lifecycle
    status            content_status_enum NOT NULL DEFAULT 'UPLOADING',
    created_at        TIMESTAMPTZ         NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ         NOT NULL DEFAULT now()
);

CREATE INDEX idx_content_lecture_id     ON content_items (lecture_id);
CREATE INDEX idx_content_course_id      ON content_items (course_id);
CREATE INDEX idx_content_instructor_id  ON content_items (instructor_id);
CREATE INDEX idx_content_type_status    ON content_items (content_type, status);
CREATE INDEX idx_content_status_created ON content_items (status, created_at);

-- ── outbox_events ─────────────────────────────────────────
-- Transactional outbox for guaranteed RabbitMQ delivery.
-- Written in the SAME transaction as the business entity.
-- A scheduler reads and publishes to RabbitMQ independently.
CREATE TABLE outbox_events
(
    id           UUID PRIMARY KEY         DEFAULT gen_random_uuid(),
    event_type   VARCHAR(100)        NOT NULL,   -- e.g. ContentUploadedEvent
    routing_key  VARCHAR(200)        NOT NULL,   -- RabbitMQ routing key
    payload      JSONB               NOT NULL,   -- Full JSON event body
    published    BOOLEAN             NOT NULL DEFAULT FALSE,
    retry_count  INTEGER             NOT NULL DEFAULT 0,
    last_error   TEXT,
    created_at   TIMESTAMPTZ         NOT NULL DEFAULT now(),
    published_at TIMESTAMPTZ
);

CREATE INDEX idx_outbox_unpublished ON outbox_events (published, created_at)
    WHERE published = FALSE;
CREATE INDEX idx_outbox_cleanup     ON outbox_events (published_at)
    WHERE published = TRUE;

-- ── Auto-update updated_at ────────────────────────────────
CREATE OR REPLACE FUNCTION fn_update_updated_at()
    RETURNS TRIGGER AS
$$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_content_items_updated_at
    BEFORE UPDATE
    ON content_items
    FOR EACH ROW
EXECUTE FUNCTION fn_update_updated_at();
