package com.example.operion.module.upload.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

/**
 * BE-23: what the phone needs to upload a file straight to storage. Send a PUT
 * to {@code putUrl} with exactly these {@code headers} and a body of exactly
 * the declared size, before {@code expiresAt}; then send {@code uploadId} with
 * the record the file belongs to.
 */
@Getter
@Builder
public class UploadTicketResponse {

    private UUID uploadId;

    private String putUrl;

    private String method;

    private Map<String, String> headers;

    private Instant expiresAt;
}
