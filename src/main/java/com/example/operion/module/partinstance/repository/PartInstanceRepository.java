package com.example.operion.module.partinstance.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.partinstance.entity.PartInstance;

public interface PartInstanceRepository extends JpaRepository<PartInstance, UUID> {

    Optional<PartInstance> findByBarcodeAndTenantId(String barcode, UUID tenantId);

    boolean existsByBarcode(String barcode);

    List<PartInstance> findByPartIdAndTenantId(UUID partId, UUID tenantId);

    @Query("select pi from PartInstance pi where pi.part.id = :partId and pi.tenant.id in :tenantIds")
    List<PartInstance> findByPartIdAndTenantIdIn(@Param("partId") UUID partId, @Param("tenantIds") List<UUID> tenantIds);

    @Query("select pi from PartInstance pi where pi.tenant.id in :tenantIds")
    List<PartInstance> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

    /**
     * Burn rate (operion-burn-rate-spec.md §2, §6.1): takes per part within
     * the window, for the whole catalog in one grouped query — not a
     * per-part loop. Row shape: [partId (UUID), takeCount (Long)].
     */
    @Query("select pi.part.id, count(pi) from PartInstance pi "
            + "where pi.tenant.id in :tenantIds and pi.takenAt >= :windowStart "
            + "group by pi.part.id")
    List<Object[]> countTakesGroupedByPart(
            @Param("tenantIds") List<UUID> tenantIds,
            @Param("windowStart") LocalDateTime windowStart);

    /**
     * Earliest takenAt per part, all-time (not window-limited) — the lower
     * clamp for observationDays (spec §2.1). Row shape: [partId (UUID),
     * firstSeen (LocalDateTime)].
     */
    @Query("select pi.part.id, min(pi.takenAt) from PartInstance pi "
            + "where pi.tenant.id in :tenantIds and pi.takenAt is not null "
            + "group by pi.part.id")
    List<Object[]> findFirstTakenAtGroupedByPart(@Param("tenantIds") List<UUID> tenantIds);
}
