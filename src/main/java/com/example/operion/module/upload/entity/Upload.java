package com.example.operion.module.upload.entity;

import java.time.LocalDateTime;
import java.util.UUID;

import com.example.operion.module.auth.entity.BaseEntity;
import com.example.operion.module.auth.entity.User;
import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.upload.enums.UploadPurpose;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * BE-23: a file in object storage (R2), not the file itself. Created when the
 * phone asks for an upload URL; attached once, by its uploader, to the record
 * it belongs to (a goods receipt, an expense claim). Unattached uploads older
 * than 24 h are swept, so abandoned photos don't accumulate.
 *
 * The id is assigned before saving because it is part of the object key.
 */
@Entity
@Table(name = "uploads")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Upload extends BaseEntity {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private UploadPurpose purpose;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @Column(name = "content_type", nullable = false, length = 100)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    /** What the upload was attached to, e.g. "GOODS_RECEIPT". Null until attached. */
    @Column(name = "attached_type", length = 50)
    private String attachedType;

    @Column(name = "attached_id")
    private UUID attachedId;

    @Column(name = "attached_at")
    private LocalDateTime attachedAt;
}
