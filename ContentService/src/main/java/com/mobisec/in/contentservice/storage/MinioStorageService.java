package com.mobisec.in.contentservice.storage;

import com.mobisec.in.contentservice.exception.StorageException;
import io.minio.*;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class MinioStorageService implements StorageService {

    private final MinioClient          minioClient;          // presigned URLs, stat, delete
    private final MinioMultipartClient minioMultipartClient; // multipart lifecycle via AWS SDK v2

    @Value("${minio.bucket-name}")
    private String bucketName;

    // ── Single-part ───────────────────────────────────────────────────────────

    @Override
    public String generatePresignedUploadUrl(String storageKey, String mimeType, Duration expiry) {
        try {
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(storageKey)
                            .expiry((int) expiry.getSeconds(), TimeUnit.SECONDS)
                            .build()
            );
            log.info("Presigned PUT URL generated: key={}, expiry={}s",
                    storageKey, expiry.getSeconds());
            return url;
        } catch (Exception e) {
            throw new StorageException("Failed to generate upload URL for: " + storageKey, e);
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String storageKey, Duration expiry) {
        try {
            return minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(bucketName)
                            .object(storageKey)
                            .expiry((int) expiry.getSeconds(), TimeUnit.SECONDS)
                            .build()
            );
        } catch (Exception e) {
            throw new StorageException("Failed to generate download URL for: " + storageKey, e);
        }
    }

    @Override
    public StorageObjectMetadata statObject(String storageKey) {
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build()
            );
            String etag = stat.etag().replace("\"", "");
            return new StorageObjectMetadata(storageKey, stat.size(), etag, stat.contentType());

        } catch (io.minio.errors.ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                throw new StorageException("Object not found in storage: " + storageKey);
            }
            throw new StorageException("Storage error during stat: " + storageKey, e);
        } catch (Exception e) {
            throw new StorageException("Storage error during stat: " + storageKey, e);
        }
    }

    @Override
    public void deleteObject(String storageKey) {
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(bucketName)
                            .object(storageKey)
                            .build()
            );
            log.info("Object deleted: key={}", storageKey);
        } catch (Exception e) {
            log.error("Failed to delete object: key={} — will retry later", storageKey, e);
        }
    }

    // ── Multipart — delegates to MinioMultipartClient (AWS SDK v2) ────────────

    @Override
    public String initiateMultipartUpload(String storageKey, String mimeType) {
        try {
            return minioMultipartClient.createMultipartUpload(storageKey, mimeType);
        } catch (Exception e) {
            log.error("Failed to initiate multipart upload: key={}", storageKey, e);
            throw new StorageException("Failed to initiate multipart upload for: " + storageKey, e);
        }
    }

    @Override
    public String generatePresignedPartUrl(String storageKey, String uploadId,
                                           int partNumber, Duration expiry) {
        try {
            // Presigned part URLs still use MinioClient — this is a URL generation
            // operation, not a lifecycle operation, so it works on the sync client.
            String url = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(bucketName)
                            .object(storageKey)
                            .expiry((int) expiry.getSeconds(), TimeUnit.SECONDS)
                            .extraQueryParams(Map.of(
                                    "uploadId",   uploadId,
                                    "partNumber", String.valueOf(partNumber)
                            ))
                            .build()
            );
            log.debug("Presigned part URL generated: key={}, part={}", storageKey, partNumber);
            return url;
        } catch (Exception e) {
            throw new StorageException(
                    "Failed to generate part URL: " + storageKey + " part=" + partNumber, e);
        }
    }

    @Override
    public void completeMultipartUpload(String storageKey, String uploadId,
                                        List<CompletedPart> parts) {
        try {
            // Convert StorageService.CompletedPart → MinioMultipartClient.CompletedPartInfo
            List<MinioMultipartClient.CompletedPartInfo> partInfos = parts.stream()
                    .map(p -> new MinioMultipartClient.CompletedPartInfo(p.partNumber(), p.etag()))
                    .toList();

            minioMultipartClient.completeMultipartUpload(storageKey, uploadId, partInfos);

        } catch (Exception e) {
            log.error("Failed to complete multipart upload: key={}", storageKey, e);
            throw new StorageException("Failed to complete multipart upload for: " + storageKey, e);
        }
    }

    @Override
    public void abortMultipartUpload(String storageKey, String uploadId) {
        try {
            minioMultipartClient.abortMultipartUpload(storageKey, uploadId);
        } catch (Exception e) {
            log.error("Failed to abort multipart upload: key={} — MinIO lifecycle will clean up",
                    storageKey, e);
        }
    }
}
