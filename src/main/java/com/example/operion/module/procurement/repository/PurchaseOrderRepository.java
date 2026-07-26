package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.procurement.entity.PurchaseOrder;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PurchaseOrder> findByTenantId(UUID tenantId);

    @Query("select po from PurchaseOrder po where po.tenant.id in :tenantIds")
    List<PurchaseOrder> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);
}
