package com.example.operion.module.upload.service;

import java.net.URI;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

/**
 * BE-23: the only class that talks to object storage. Cloudflare R2 speaks the
 * S3 API, so the AWS SDK is used unchanged with R2's endpoint and region "auto".
 *
 * The bucket is private: nothing is ever public. The phone uploads straight to
 * R2 with a short-lived presigned PUT, and reads with a short-lived presigned
 * GET, so file bytes never pass through this server.
 *
 * Configured by env vars R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY,
 * R2_BUCKET (and optionally R2_ENDPOINT, used by tests to point at a fake
 * endpoint). With any of them missing, upload endpoints answer 503 and the app
 * still boots, so local development doesn't need an R2 account.
 */
@Component
@ConditionalOnProperty(name = "uploads.storage", havingValue = "r2")
public class R2Storage implements ObjectStorage {

    private final String bucket;

    private final S3Presigner presigner;

    private final S3Client client;

    public R2Storage(
            @Value("${r2.account-id:}") String accountId,
            @Value("${r2.access-key-id:}") String accessKeyId,
            @Value("${r2.secret-access-key:}") String secretAccessKey,
            @Value("${r2.bucket:}") String bucket,
            @Value("${r2.endpoint:}") String endpointOverride) {

        String endpoint = !endpointOverride.isBlank()
                ? endpointOverride
                : (!accountId.isBlank() ? "https://" + accountId + ".r2.cloudflarestorage.com" : "");

        boolean configured = !endpoint.isBlank() && !accessKeyId.isBlank()
                && !secretAccessKey.isBlank() && !bucket.isBlank();

        this.bucket = bucket;

        if (!configured) {
            this.presigner = null;
            this.client = null;
            return;
        }

        StaticCredentialsProvider credentials = StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey));

        // R2 wants path-style addressing on its account endpoint
        S3Configuration pathStyle = S3Configuration.builder().pathStyleAccessEnabled(true).build();

        this.presigner = S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .build();

        this.client = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of("auto"))
                .credentialsProvider(credentials)
                .serviceConfiguration(pathStyle)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    @Override
    public boolean isConfigured() {
        return presigner != null;
    }

    /**
     * A PUT URL valid for {@code ttl}. Content-Type and Content-Length are part
     * of the signature, so the upload must match what was declared: a bigger
     * file, or a different type, is rejected by R2 itself.
     */
    @Override
    public Presigned presignPut(String key, String contentType, long sizeBytes, Duration ttl) {

        requireConfigured();

        var request = PutObjectRequest.builder()
                .bucket(bucket)
                .key(key)
                .contentType(contentType)
                .contentLength(sizeBytes)
                .build();

        var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .putObjectRequest(request)
                .build());

        return new Presigned(presigned.url().toString(), presigned.expiration());
    }

    @Override
    public Presigned presignGet(String key, Duration ttl) {

        requireConfigured();

        var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(ttl)
                .getObjectRequest(GetObjectRequest.builder().bucket(bucket).key(key).build())
                .build());

        return new Presigned(presigned.url().toString(), presigned.expiration());
    }

    /** True if the object was actually uploaded. Used before attaching. */
    @Override
    public boolean exists(String key) {

        requireConfigured();

        try {
            client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    /** Deleting a key that doesn't exist is not an error in S3/R2. */
    @Override
    public void delete(String key) {

        requireConfigured();

        client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    }

    /** 503 "File uploads are not configured on this server" when R2 env vars are missing. */
    @Override
    public void requireConfigured() {

        if (!isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "File uploads are not configured on this server");
        }
    }
}
