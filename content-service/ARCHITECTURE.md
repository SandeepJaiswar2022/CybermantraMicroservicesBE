# Content Service — Architecture Guide

## Package Structure

```
com.lms.content/
├── ContentServiceApplication.java
├── config/
│   ├── AsyncConfig.java           # Upload thread pool (5–20 threads)
│   ├── CacheConfig.java           # Redis TTL per cache region
│   ├── CloudinaryConfig.java      # Cloudinary SDK bean
│   ├── CorrelationIdFilter.java   # X-Correlation-ID per request
│   ├── MinioConfig.java           # MinIO client + bucket init
│   ├── MinioProperties.java       # @ConfigurationProperties binding
│   └── RabbitMQConfig.java        # Exchange/queue/binding topology
├── controller/
│   └── ContentController.java     # REST endpoints
├── domain/
│   ├── entity/
│   │   └── ContentItem.java       # Core domain entity (no storage key in API)
│   └── enums/
│       ├── ContentType.java       # VIDEO | RESOURCE | THUMBNAIL
│       ├── ContentStatus.java     # UPLOADING → READY → DELETED
│       └── StorageProvider.java   # CLOUDINARY | MINIO | CDN
├── dto/
│   ├── request/
│   │   └── ContentUploadRequest.java
│   └── response/
│       ├── ApiResponse.java       # Standard envelope {success, data, error, timestamp}
│       ├── ContentItemResponse.java
│       └── SignedUrlResponse.java
├── exception/
│   ├── ContentNotFoundException.java
│   ├── GlobalExceptionHandler.java  # Maps all exceptions to ApiResponse
│   ├── InvalidFileException.java
│   ├── OwnershipException.java
│   └── StorageException.java
├── mapper/
│   └── ContentItemMapper.java     # MapStruct (compile-time, zero reflection)
├── messaging/
│   ├── ContentEventPublisher.java # Publishes events to RabbitMQ
│   └── ContentEventListener.java  # Consumes transcode-complete events
├── repository/
│   └── ContentItemRepository.java # JPA + projections (no N+1, no SELECT *)
├── security/
│   ├── AuthenticatedUser.java     # JWT claim extractor
│   ├── RsaKeyUtil.java            # PEM → RSAPublicKey loader
│   └── SecurityConfig.java        # RSA JWT validation + role mapping
├── service/
│   ├── ContentService.java        # Interface (contract)
│   ├── impl/
│   │   └── ContentServiceImpl.java  # Business logic
│   └── storage/
│       ├── StorageService.java      # ← KEY ABSTRACTION (swap providers here)
│       ├── CloudinaryStorageService.java
│       └── MinioStorageService.java
└── util/
    ├── CorrelationIdUtil.java     # MDC wrapper
    └── FileValidationUtil.java    # Apache Tika MIME detection + size limits
```

---

## Storage Strategy: Cloudinary vs MinIO

### Cloudinary (Option A)
**Best for:** MVP, low infra budget, fast iteration.
- No server to manage — SaaS.
- Built-in CDN & image transformations.
- Signed URLs natively supported.
- **Free tier:** 25 GB storage + 25 GB bandwidth.
- **Con:** Costly at scale; data leaves your infra.

### MinIO (Option B)
**Best for:** Your situation (no AWS budget, want full control).
- Self-hosted on a $10–20/month VPS with 200 GB SSD.
- S3-compatible: migrate to AWS S3 by changing one endpoint.
- Presigned URLs with byte-range support (video seeking works).
- Add Cloudflare (free) in front for CDN.
- **Con:** You manage infrastructure, disk, backups.

### Recommendation for your case:
**Use MinIO.** You get zero ongoing storage cost, full control,
a clear migration path to S3, and Cloudflare gives you CDN for free.

---

## Performance Design

### 1000 Concurrent Users
- **Tomcat threads:** 200 (default) handle API requests.
- **HikariCP:** 10 connections max (right-sized for PostgreSQL).
- **Upload threads:** 20 max, isolated from API threads.
- **Redis cache:** Lecture content lists cached 5 min, reducing DB load by ~80%.

### Streaming — No Memory Overload
- MinIO: `putObject(..., inputStream, size, -1)` streams directly from multipart to MinIO. No byte[] in heap.
- Cloudinary: `file.getBytes()` — still in memory; switch to chunked upload for >500 MB.
- Signed URL redirect: client downloads directly from storage. Zero video bytes flow through this service.

### N+1 Prevention
- Repository uses specific queries with projections, not `findAll()`.
- `findByLectureId` fetches all content in one query.
- Projections (`ContentSummary`) select only needed columns.

### DB Indexing
- `lecture_id` — most frequent access pattern.
- `course_id` — cascade deletes + stats.
- `instructor_id` — dashboard queries.
- `(content_type, status)` — composite for filtered lists.

---

## Future Extension Points

### 1. CDN Integration
Switch `StorageProvider` enum to `CDN`. Add `CdnStorageService implements StorageService`.
No service layer changes required.

### 2. Video Transcoding (HLS Streaming)
```
ContentServiceImpl.upload()
  → publishTranscodeRequest(contentId, storageKey)
  → [Transcoding Service: FFmpeg → HLS segments → MinIO]
  → onTranscodeComplete(contentId, hlsManifestKey)  ← already implemented
```
The `hls_manifest_key` column in DB is ready to store HLS playlist location.
Client uses `hlsManifestKey` URL instead of direct video URL.

### 3. Enrollment-Based Access Control
Currently only instructor + admin can get signed URLs.
Add: check enrollment service (via Redis cache) in `generateSignedUrl()`.
Redis key: `enrolled:{userId}:{courseId}` → boolean.

---

## Configuration Reference

### Switch Storage Provider
```yaml
content:
  storage:
    provider: minio   # or "cloudinary"
```
Spring's `@ConditionalOnProperty` activates the correct implementation bean.
No code changes required.

### HikariCP Tuning Rationale
- `maximum-pool-size: 10` — Each PostgreSQL connection costs ~5 MB RAM. 10 = 50 MB. More connections don't always mean more throughput; PostgreSQL serializes at write-lock level.
- `connection-timeout: 30000` — Fail fast; don't queue threads for minutes.
- `leak-detection-threshold: 60000` — Catch unclosed connections in dev.

### Multipart Tuning
```yaml
spring.servlet.multipart:
  max-file-size: 5GB
  file-size-threshold: 10MB   # Spools to disk above this; avoids OOM
```
Match `max-file-size` in Nginx: `client_max_body_size 5g;`
