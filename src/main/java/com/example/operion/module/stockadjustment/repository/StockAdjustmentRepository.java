package com.example.operion.module.stockadjustment.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.stockadjustment.entity.StockAdjustment;
import com.example.operion.module.stockadjustment.enums.StockAdjustmentStatus;

public interface StockAdjustmentRepository extends JpaRepository<StockAdjustment, UUID> {

    Optional<StockAdjustment> findByIdAndTenantId(UUID id, UUID tenantId);

    @Query("select sa from StockAdjustment sa where sa.id = :id and sa.tenant.id in :tenantIds")
    Optional<StockAdjustment> findByIdAndTenantIdIn(
            @Param("id") UUID id,
            @Param("tenantIds") List<UUID> tenantIds);

    @Query("select sa from StockAdjustment sa where sa.tenant.id in :tenantIds order by sa.createdAt desc")
    List<StockAdjustment> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

    @Query("select sa from StockAdjustment sa where sa.tenant.id in :tenantIds and sa.status = :status order by sa.createdAt desc")
    List<StockAdjustment> findByTenantIdInAndStatus(
            @Param("tenantIds") List<UUID> tenantIds,
            @Param("status") StockAdjustmentStatus status);
}
