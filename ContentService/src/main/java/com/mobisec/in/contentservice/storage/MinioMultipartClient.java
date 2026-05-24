package com.mobisec.in.contentservice.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.net.URI;
import java.util.List;

/**
 * Handles the multipart upload lifecycle (initiate / complete / abort)
 * using the AWS SDK v2 pointed at MinIO.
 *
 * WHY NOT SUBCLASS MinioAsyncClient:
 *   - MinioAsyncClient.Builder is final — cannot be extended
 *   - Protected methods throw checked exceptions that are incompatible
 *     with CompletableFuture signatures — compiler errors on every wrapper
 *   - abortMultipartUpload already exists on S3Base with a different
 *     return type — causes an incompatible override compile error
 *
 * WHY AWS SDK v2:
 *   - MinIO is fully S3-compatible — the AWS SDK works against it out of the box
 *   - Clean public API for all three multipart operations with no hacks
 *   - forcePathStyle(true) is the only MinIO-specific setting required
 */
@Component
@Slf4j
public class MinioMultipartClient {

    private final S3Client s3Client;
    private final String   bucketName;

    public MinioMultipartClient(
            @Value("${minio.endpoint}")    String endpoint,
            @Value("${minio.access-key}")  String accessKey,
            @Value("${minio.secret-key}")  String secretKey,
            @Value("${minio.bucket-name}") String bucketName) {

        this.bucketName = bucketName;

        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                // MinIO ignores the region value but the AWS SDK requires one
                .region(Region.US_EAST_1)
                // MinIO requires path-style URLs: http://localhost:9000/bucket/key
                // (virtual-hosted style http://bucket.localhost:9000/key does not work locally)
                .forcePathStyle(true)
                .build();
    }

    /**
     * Initiate a multipart upload.
     * Returns the uploadId that must be passed to all subsequent part operations.
     */
    public String createMultipartUpload(String storageKey, String mimeType) {
        log.info("Initiating multipart upload: key={}", storageKey);

        CreateMultipartUploadResponse response = s3Client.createMultipartUpload(
                CreateMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(storageKey)
                        .contentType(mimeType)
                        .build()
        );

        log.info("Multipart upload initiated: key={}, uploadId={}", storageKey, response.uploadId());
        return response.uploadId();
    }

    /**
     * Complete a multipart upload — MinIO assembles all parts into the final object.
     * Parts must be in ascending partNumber order.
     */
    public void completeMultipartUpload(String storageKey, String uploadId,
                                        List<CompletedPartInfo> parts) {
        log.info("Completing multipart upload: key={}, parts={}", storageKey, parts.size());

        List<CompletedPart> sdkParts = parts.stream()
                .map(p -> CompletedPart.builder()
                        .partNumber(p.partNumber())
                        .eTag(p.etag())
                        .build())
                .toList();

        s3Client.completeMultipartUpload(
                CompleteMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(storageKey)
                        .uploadId(uploadId)
                        .multipartUpload(CompletedMultipartUpload.builder()
                                .parts(sdkParts)
                                .build())
                        .build()
        );

        log.info("Multipart upload completed: key={}", storageKey);
    }

    /**
     * Abort a multipart upload — frees all temporary part storage on MinIO.
     * Called when an upload fails, is abandoned, or content is deleted mid-upload.
     */
    public void abortMultipartUpload(String storageKey, String uploadId) {
        log.info("Aborting multipart upload: key={}, uploadId={}", storageKey, uploadId);

        s3Client.abortMultipartUpload(
                AbortMultipartUploadRequest.builder()
                        .bucket(bucketName)
                        .key(storageKey)
                        .uploadId(uploadId)
                        .build()
        );

        log.info("Multipart upload aborted: key={}", storageKey);
    }

    /**
     * Simple value type — carries completed part info without any SDK dependency
     * leaking into the rest of the codebase.
     */
    public record CompletedPartInfo(int partNumber, String etag) {}
}