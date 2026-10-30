package com.example.operion.module.upload.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.tenant.entity.Tenant;
import com.example.operion.module.upload.entity.Upload;

public interface UploadRepository extends JpaRepository<Upload, UUID> {

    Optional<Upload> findByIdAndTenantIdIn(UUID id, List<UUID> tenantIds);

    List<Upload> findByAttachedTypeAndAttachedId(String attachedType, UUID attachedId);

    List<Upload> findByAttachedTypeIsNullAndCreatedAtBefore(LocalDateTime cutoff);

    /**
     * Attach in one conditional UPDATE, so two simultaneous attach calls can't
     * both succeed: only the one that still sees attached_type IS NULL wins.
     * Returns the number of rows changed (0 = already attached). BE-40: also
     * sets the upload's tenant to the record's.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Upload u set u.attachedType = :type, u.attachedId = :targetId, u.attachedAt = :at, "
            + "u.tenant = :tenant where u.id = :id and u.attachedType is null")
    int attachIfUnattached(
            @Param("id") UUID id,
            @Param("type") String type,
            @Param("targetId") UUID targetId,
            @Param("tenant") Tenant tenant,
            @Param("at") LocalDateTime at);
}
