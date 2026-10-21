package com.example.operion.module.upload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.operion.module.upload.service.R2Storage;

/**
 * BE-23: presigning with the real AWS SDK against a fake endpoint. Presigning is
 * pure local signing, so no network call happens and no R2 account is needed
 * ("no real R2 calls in CI").
 */
class R2StorageTest {

    private final R2Storage storage = new R2Storage(
            "", "test-access-key", "test-secret-key", "operion-test", "http://localhost:9999");

    @Test
    void putUrl_isSignedForThatKey_typeAndSize_andExpiresIn10Minutes() {

        Instant before = Instant.now();
        R2Storage.Presigned put = storage.presignPut(
                "tenant-1/goods_receipt_photo/abc", "image/jpeg", 345_678, Duration.ofMinutes(10));

        URI url = URI.create(put.url());
        String query = url.getQuery();

        assertEquals("localhost", url.getHost());
        assertEquals(9999, url.getPort());
        assertEquals("/operion-test/tenant-1/goods_receipt_photo/abc", url.getPath(), "path-style, bucket first");
        assertTrue(query.contains("X-Amz-Signature="));
        assertTrue(query.contains("X-Amz-Expires=600"));
        // type and size are part of the signature, so R2 rejects a bigger or different file
        String signedHeaders = query.replaceAll(".*X-Amz-SignedHeaders=([^&]*).*", "$1");
        assertTrue(signedHeaders.contains("content-type"), signedHeaders);
        assertTrue(signedHeaders.contains("content-length"), signedHeaders);
        assertTrue(!put.expiresAt().isBefore(before.plusSeconds(599)));
    }

    @Test
    void getUrl_isSignedAndShortLived() {

        R2Storage.Presigned get = storage.presignGet("tenant-1/expense_receipt/xyz", Duration.ofMinutes(5));

        assertTrue(get.url().contains("/operion-test/tenant-1/expense_receipt/xyz"));
        assertTrue(get.url().contains("X-Amz-Expires=300"));
        assertTrue(get.url().contains("X-Amz-Signature="));
    }

    @Test
    void missingConfig_bootsButAnswers503() {

        R2Storage unconfigured = new R2Storage("", "", "", "", "");

        assertFalse(unconfigured.isConfigured());
        ResponseStatusException e = assertThrows(ResponseStatusException.class, unconfigured::requireConfigured);
        assertEquals(503, e.getStatusCode().value());
    }

    @Test
    void accountId_buildsTheR2Endpoint() {

        R2Storage fromAccount = new R2Storage("abc123", "k", "s", "bucket", "");

        assertTrue(fromAccount.isConfigured());
        assertTrue(fromAccount.presignGet("x", Duration.ofMinutes(1)).url()
                .startsWith("https://abc123.r2.cloudflarestorage.com/bucket/x"));
    }
}
