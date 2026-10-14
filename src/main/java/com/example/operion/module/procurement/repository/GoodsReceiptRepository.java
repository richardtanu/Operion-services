package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.operion.module.procurement.entity.GoodsReceipt;

public interface GoodsReceiptRepository
        extends JpaRepository<GoodsReceipt, UUID>, JpaSpecificationExecutor<GoodsReceipt> {

    Optional<GoodsReceipt> findByIdAndTenantId(UUID id, UUID tenantId);

    Optional<GoodsReceipt> findByIdAndTenantIdIn(UUID id, List<UUID> tenantIds);

    List<GoodsReceipt> findByPurchaseOrderId(UUID purchaseOrderId);

    List<GoodsReceipt> findByRealisasiId(UUID realisasiId);
}
