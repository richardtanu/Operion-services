package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.operion.module.procurement.entity.PurchaseOrderStatusHistory;

public interface PurchaseOrderStatusHistoryRepository
        extends JpaRepository<PurchaseOrderStatusHistory, UUID> {

    List<PurchaseOrderStatusHistory> findByPurchaseOrder_IdOrderByCreatedAtDesc(
            UUID purchaseOrderId);
}
