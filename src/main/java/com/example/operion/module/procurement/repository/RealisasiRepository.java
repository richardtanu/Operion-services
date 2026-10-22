package com.example.operion.module.procurement.repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.procurement.entity.Realisasi;
import com.example.operion.module.procurement.enums.RealisasiStatus;

public interface RealisasiRepository extends JpaRepository<Realisasi, UUID> {

    Optional<Realisasi> findByIdAndTenantId(UUID id, UUID tenantId);

    boolean existsByExternalOrderRef(String externalOrderRef);

    @Query("select r from Realisasi r where r.tenant.id in :tenantIds")
    List<Realisasi> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

    /** BE-16: a store's purchases in one status, purchased in [from, to). */
    @Query("select r from Realisasi r where r.tenant.id = :tenantId and r.status = :status"
            + " and r.purchasedAt >= :from and r.purchasedAt < :to")
    List<Realisasi> findPurchasedBetween(@Param("tenantId") UUID tenantId, @Param("status") RealisasiStatus status,
            @Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    List<Realisasi> findByPurchaseRequestAuthorizationId(UUID purchaseRequestAuthorizationId);

    List<Realisasi> findByPurchaseRequestAuthorizationIdAndStatus(
            UUID purchaseRequestAuthorizationId, RealisasiStatus status);
}
