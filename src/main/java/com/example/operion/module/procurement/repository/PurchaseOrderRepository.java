package com.example.operion.module.procurement.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.operion.module.procurement.entity.PurchaseOrder;
import com.example.operion.module.procurement.enums.PurchaseOrderStatus;

public interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, UUID> {

    Optional<PurchaseOrder> findByIdAndTenantId(UUID id, UUID tenantId);

    List<PurchaseOrder> findByTenantId(UUID tenantId);

    /** BE-16: a store's orders in the given statuses, ordered in [from, to). */
    @Query("select po from PurchaseOrder po where po.tenant.id = :tenantId and po.status in :statuses"
            + " and po.orderDate >= :from and po.orderDate < :to")
    List<PurchaseOrder> findOrderedBetween(@Param("tenantId") UUID tenantId,
            @Param("statuses") Collection<PurchaseOrderStatus> statuses,
            @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("select po from PurchaseOrder po where po.tenant.id in :tenantIds")
    List<PurchaseOrder> findByTenantIdIn(@Param("tenantIds") List<UUID> tenantIds);
}
