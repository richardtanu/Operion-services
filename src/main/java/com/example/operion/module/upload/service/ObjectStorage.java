package com.example.operion.module.upload.service;

import java.time.Duration;
import java.time.Instant;

/**
 * Where upload bytes live. BE-23 built {@link R2Storage} (Cloudflare R2);
 * BE-30 added {@link LocalDiskStorage} for running without an R2 bucket.
 * Chosen by {@code uploads.storage} ({@code UPLOAD_STORAGE}): {@code local}
 * (default) or {@code r2}.
 *
 * The contract is the same either way: the phone gets a short-lived signed
 * PUT URL, sends the bytes there with exactly the returned headers and no
 * login, and later opens a short-lived signed GET URL. Switching storage
 * needs no app change.
 */
public interface ObjectStorage {

    boolean isConfigured();

    /** 503 "File uploads are not configured on this server" when it isn't. */
    void requireConfigured();

    /** A PUT URL valid for {@code ttl}; the upload must match the declared type and size. */
    Presigned presignPut(String key, String contentType, long sizeBytes, Duration ttl);

    Presigned presignGet(String key, Duration ttl);

    /** True if the object was actually uploaded. Used before attaching. */
    boolean exists(String key);

    /** Deleting a key that doesn't exist is not an error. */
    void delete(String key);

    record Presigned(String url, Instant expiresAt) {
    }
}
