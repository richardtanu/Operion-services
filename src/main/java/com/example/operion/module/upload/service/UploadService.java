package com.example.operion.module.upload.service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.example.operion.common.security.TenantContext;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.auth.repository.UserRepository;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.tenant.repository.TenantRepository;
import com.example.operion.module.tenant.service.TenantHierarchyService;
import com.example.operion.module.upload.dto.CreateUploadRequest;
import com.example.operion.module.upload.dto.UploadTicketResponse;
import com.example.operion.module.upload.dto.UploadViewResponse;
import com.example.operion.module.upload.entity.Upload;
import com.example.operion.module.upload.enums.UploadPurpose;
import com.example.operion.module.upload.repository.UploadRepository;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;

/**
 * BE-23: photos and receipts. The rules:
 * <ul>
 * <li>Only JPEG, PNG, WebP or PDF, at most 5 MB.</li>
 * <li>The server only hands out short-lived presigned URLs; the phone moves
 *     the bytes to and from R2 directly.</li>
 * <li>An upload is attached once, only by its uploader, only within its own
 *     tenant ({@link #attach}). Every refusal answers like a missing id, so a
 *     caller can't probe other people's upload ids.</li>
 * <li>Unattached uploads older than 24 h are deleted ({@link #sweepAbandoned}).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    static final long MAX_BYTES = 5L * 1024 * 1024;

    static final Set<String> ALLOWED_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "application/pdf");

    static final Duration PUT_TTL = Duration.ofMinutes(10);

    static final Duration GET_TTL = Duration.ofMinutes(5);

    static final Duration ABANDONED_AFTER = Duration.ofHours(24);

    static final String NOT_FOUND = "Upload not found";

    private final UploadRepository repository;

    private final R2Storage storage;

    private final UserRepository userRepository;

    private final TenantRepository tenantRepository;

    private final TenantHierarchyService tenantHierarchyService;

    @Transactional
    public UploadTicketResponse create(CreateUploadRequest request) {

        String contentType = request.getContentType().trim().toLowerCase(Locale.ROOT);

        if (!ALLOWED_TYPES.contains(contentType)) {
            throw new RuntimeException("Unsupported file type: use JPEG, PNG, WebP or PDF");
        }

        if (request.getSizeBytes() > MAX_BYTES) {
            throw new RuntimeException("File too large: the limit is 5 MB");
        }

        // 503 before anything is written when storage isn't set up
        storage.requireConfigured();

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());
        Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();

        UUID id = UUID.randomUUID();
        String key = tenantId + "/" + request.getPurpose().name().toLowerCase(Locale.ROOT) + "/" + id;

        repository.save(Upload.builder()
                .id(id)
                .tenant(tenant)
                .uploadedBy(currentUser())
                .purpose(request.getPurpose())
                .objectKey(key)
                .contentType(contentType)
                .sizeBytes(request.getSizeBytes())
                .build());

        R2Storage.Presigned put = storage.presignPut(key, contentType, request.getSizeBytes(), PUT_TTL);

        return UploadTicketResponse.builder()
                .uploadId(id)
                .putUrl(put.url())
                .method("PUT")
                .headers(Map.of("Content-Type", contentType))
                .expiresAt(put.expiresAt())
                .build();
    }

    /**
     * A short-lived view link. Visible across the caller's tenant tree (own
     * tenant + descendants), like the records uploads attach to; an upload
     * that isn't attached yet is visible only to its uploader.
     */
    public UploadViewResponse view(UUID id) {

        List<UUID> tenantIds = tenantHierarchyService.getEffectiveTenantIds(
                UUID.fromString(TenantContext.getTenantId()));

        Upload upload = repository.findByIdAndTenantIdIn(id, tenantIds)
                .filter(u -> u.getAttachedType() != null || isCaller(u.getUploadedBy()))
                .orElseThrow(() -> new RuntimeException(NOT_FOUND));

        R2Storage.Presigned get = storage.presignGet(upload.getObjectKey(), GET_TTL);

        return UploadViewResponse.builder()
                .uploadId(upload.getId())
                .purpose(upload.getPurpose())
                .contentType(upload.getContentType())
                .sizeBytes(upload.getSizeBytes())
                .getUrl(get.url())
                .expiresAt(get.expiresAt())
                .attached(upload.getAttachedType() != null)
                .build();
    }

    /**
     * For the records that take files (BE-20 goods photos, BE-19 receipts):
     * claim an upload for {@code attachedType}/{@code attachedId}. Call it in
     * the same transaction that saves the record.
     *
     * Refused, all as "Upload not found": another tenant's upload, someone
     * else's upload, the wrong purpose, or a file that was never actually
     * PUT to storage. Refused as "already attached": the second attach.
     */
    @Transactional
    public Upload attach(UUID uploadId, UploadPurpose expectedPurpose, String attachedType, UUID attachedId) {

        UUID tenantId = UUID.fromString(TenantContext.getTenantId());

        Upload upload = repository.findById(uploadId)
                .filter(u -> u.getTenant().getId().equals(tenantId))
                .filter(u -> isCaller(u.getUploadedBy()))
                .filter(u -> u.getPurpose() == expectedPurpose)
                .orElseThrow(() -> new RuntimeException(NOT_FOUND));

        if (upload.getAttachedType() != null) {
            throw new RuntimeException("Upload is already attached");
        }

        if (!storage.exists(upload.getObjectKey())) {
            throw new RuntimeException(NOT_FOUND);
        }

        int changed = repository.attachIfUnattached(uploadId, attachedType, attachedId, LocalDateTime.now());

        if (changed == 0) {
            throw new RuntimeException("Upload is already attached");
        }

        return repository.findById(uploadId).orElseThrow();
    }

    /** Ids of the uploads attached to one record, e.g. a goods receipt's photos (BE-20). */
    public List<UUID> attachedIds(String attachedType, UUID attachedId) {

        return repository.findByAttachedTypeAndAttachedId(attachedType, attachedId).stream()
                .map(Upload::getId)
                .toList();
    }

    /**
     * Hourly: delete uploads nobody attached within 24 h (the phone asked for a
     * URL, then the record was never saved). The object goes first, then the row,
     * so a failure leaves the row to retry next hour rather than an orphan file.
     */
    @Scheduled(cron = "0 15 * * * *")
    @Transactional
    public void sweepAbandoned() {

        if (!storage.isConfigured()) {
            return;
        }

        List<Upload> abandoned = repository.findByAttachedTypeIsNullAndCreatedAtBefore(
                LocalDateTime.now().minus(ABANDONED_AFTER));

        for (Upload upload : abandoned) {
            try {
                storage.delete(upload.getObjectKey());
                repository.delete(upload);
            } catch (RuntimeException e) {
                log.warn("Could not delete abandoned upload {}: {}", upload.getId(), e.getMessage());
            }
        }

        if (!abandoned.isEmpty()) {
            log.info("Swept {} abandoned upload(s)", abandoned.size());
        }
    }

    private boolean isCaller(User user) {

        return user.getId().toString().equals(
                SecurityContextHolder.getContext().getAuthentication().getName());
    }

    private User currentUser() {

        UUID userId = UUID.fromString(SecurityContextHolder.getContext().getAuthentication().getName());

        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Current user not found"));
    }
}
