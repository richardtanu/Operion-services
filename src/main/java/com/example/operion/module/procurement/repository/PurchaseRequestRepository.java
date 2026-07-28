package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.procurement.entity.PurchaseRequest;

public interface PurchaseRequestRepository extends JpaRepository<PurchaseRequest, UUID> {

    Optional<PurchaseRequest> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PurchaseRequest> findByTenantId(UUID tenantId);

    @Query("select pr from PurchaseRequest pr where pr.tenant.id in :tenantIds")
    List<PurchaseRequest> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);

    List<PurchaseRequest> findByIdInAndTenantId(List<UUID> ids, UUID tenantId);

    List<PurchaseRequest> findByPurchaseOrderId(UUID purchaseOrderId);

    List<PurchaseRequest> findByPurchaseRequestAuthorizationId(UUID purchaseRequestAuthorizationId);
}
