package com.example.operion.module.procurement.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.example.operion.module.procurement.entity.PurchaseRequestStatusHistory;

public interface PurchaseRequestStatusHistoryRepository
        extends JpaRepository<PurchaseRequestStatusHistory, UUID>, JpaSpecificationExecutor<PurchaseRequestStatusHistory> {

    List<PurchaseRequestStatusHistory> findByPurchaseRequest_IdOrderByCreatedAtDesc(
            UUID purchaseRequestId);
}
