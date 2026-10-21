package com.example.operion.module.upload.dto;

import java.time.Instant;
import java.util.UUID;

import com.example.operion.module.upload.enums.UploadPurpose;

import lombok.Builder;
import lombok.Getter;

/** BE-23: a short-lived link to view or download a stored file. */
@Getter
@Builder
public class UploadViewResponse {

    private UUID uploadId;

    private UploadPurpose purpose;

    private String contentType;

    private Long sizeBytes;

    private String getUrl;

    private Instant expiresAt;

    private boolean attached;
}
